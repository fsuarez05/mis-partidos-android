package uy.federico.mispartidos;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Xml;
import org.json.JSONArray;
import org.xmlpull.v1.XmlPullParser;
import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

final class NewsRepository {
    interface Callback { void onComplete(List<NewsItem> items,boolean updated,String message); }

    static final long CACHE_MS=4L*60L*60L*1000L;
    private static final int MAX_PER_TEAM=6;
    private static final int MAX_TOTAL=60;
    private static final long MAX_AGE_MS=7L*24L*60L*60L*1000L;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static final ExecutorService FEEDS=Executors.newFixedThreadPool(3);
    private final Context context;
    private final SharedPreferences prefs;

    NewsRepository(Context context){
        this.context=context.getApplicationContext();
        prefs=this.context.getSharedPreferences("mis_partidos",Context.MODE_PRIVATE);
    }

    List<NewsItem> cached(){return readCache();}
    long lastUpdate(){return prefs.getLong("news_updated_at",0);}
    boolean cacheFresh(){return !readCache().isEmpty()&&System.currentTimeMillis()-lastUpdate()<CACHE_MS;}

    void load(boolean force,Callback callback){
        List<NewsItem> cache=readCache();
        EXECUTOR.execute(()->refresh(cache,force,callback));
    }

    private String stampKey(TeamQuery team){
        return "news_team_updated_"+(team.national?"national_":"club_")+normalize(team.name);
    }

    private void refresh(List<NewsItem> old,boolean force,Callback callback){
        AppStore store=new AppStore(context);
        List<TeamQuery> all=new ArrayList<>();
        for(String team:store.selectedTeams())all.add(new TeamQuery(team,false));
        for(String team:store.selectedNationalTeams())all.add(new TeamQuery(team,true));
        Collections.sort(all,(a,b)->a.name.compareToIgnoreCase(b.name));
        if(all.isEmpty()){post(callback,new ArrayList<>(),false,"Elegí al menos un club o selección para ver noticias.");return;}

        long now=System.currentTimeMillis();
        Set<String> active=new HashSet<>();
        List<TeamQuery> pending=new ArrayList<>();
        for(TeamQuery team:all){
            active.add(team.name);
            long updated=prefs.getLong(stampKey(team),0L);
            if(force||updated<=0||now-updated>=CACHE_MS)pending.add(team);
        }
        List<NewsItem> merged=new ArrayList<>();
        for(NewsItem item:old)if(active.contains(item.team))merged.add(item);
        if(pending.isEmpty()){
            post(callback,dedupe(merged),false,"Noticias guardadas · todos los equipos actualizados");
            return;
        }

        List<Future<List<NewsItem>>> tasks=new ArrayList<>();
        for(TeamQuery team:pending)tasks.add(FEEDS.submit(()->fetch(team)));
        int successes=0,failures=0;
        SharedPreferences.Editor editor=prefs.edit();
        for(int i=0;i<pending.size();i++){
            TeamQuery team=pending.get(i);
            try{
                List<NewsItem> items=tasks.get(i).get(25,TimeUnit.SECONDS);
                merged.removeIf(item->item.team.equals(team.name));
                merged.addAll(items);
                editor.putLong(stampKey(team),System.currentTimeMillis());
                successes++;
            }catch(Exception e){
                tasks.get(i).cancel(true);
                failures++;
            }
        }
        editor.apply();
        merged=dedupe(merged);
        if(successes>0)saveCache(merged);
        String message=failures==0?"Noticias actualizadas · "+successes+" equipos":(
            successes==0?"No se pudo actualizar. Se muestran noticias guardadas.":"Actualización parcial: "+successes+" equipos actualizados, "+failures+" pendientes.");
        post(callback,merged,successes>0,message);
    }

    private void post(Callback callback,List<NewsItem> items,boolean updated,String message){
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.onComplete(items,updated,message));
    }

    private List<NewsItem> fetch(TeamQuery team) throws Exception {
        String query=queryFor(team)+" -femenino -femenina -women -womens -femeni -sub19 -sub17 -juvenil";
        String address="https://news.google.com/rss/search?q="+Uri.encode(query+" when:7d")+"&hl=es-419&gl=UY&ceid=UY:es-419";
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(10000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent","MisPartidos/1.0 Android");
        int code=connection.getResponseCode();
        if(code<200||code>=300){connection.disconnect();throw new Exception("HTTP "+code);}
        List<NewsItem> result=new ArrayList<>();
        try(BufferedInputStream input=new BufferedInputStream(connection.getInputStream())){
            XmlPullParser parser=Xml.newPullParser();parser.setInput(input,"UTF-8");
            boolean inItem=false;String title="",link="",source="",date="";
            int event=parser.getEventType();
            while(event!=XmlPullParser.END_DOCUMENT&&result.size()<MAX_PER_TEAM){
                if(event==XmlPullParser.START_TAG){
                    String tag=parser.getName();
                    if("item".equals(tag)){inItem=true;title="";link="";source="";date="";}
                    else if(inItem&&("title".equals(tag)||"link".equals(tag)||"source".equals(tag)||"pubDate".equals(tag))){
                        String value=parser.nextText();
                        if("title".equals(tag))title=value;else if("link".equals(tag))link=value;else if("source".equals(tag))source=value;else date=value;
                    }
                }else if(event==XmlPullParser.END_TAG&&"item".equals(parser.getName())){
                    inItem=false;long published=parseDate(date);
                    if(published>0 && published<=System.currentTimeMillis()+60L*60L*1000L && published>=System.currentTimeMillis()-MAX_AGE_MS && isRelevant(team,title,source) && !expiredPreview(title,published) && !historicalHeadline(title) && !excludedNews(title,source))result.add(new NewsItem(cleanTitle(title,source),source,link,team.name,published));
                }
                event=parser.next();
            }
        }finally{connection.disconnect();}
        return result;
    }

    private String queryFor(TeamQuery team){
        String name=team.name;
        if(team.national)return "\"selección de "+name+"\" fútbol";
        if("Liverpool (Uruguay)".equals(name))return "\"Liverpool Fútbol Club\" Montevideo Uruguay -England -Premier";
        if("Liverpool".equals(name))return "\"Liverpool FC\" England Premier League";
        if("Inter".equals(name))return "\"Inter de Milán\" OR \"Inter Milan\" fútbol";
        if("Milan".equals(name))return "\"AC Milan\" fútbol Italia";
        if("Nacional".equals(name))return "\"Club Nacional de Football\" Uruguay";
        if("River Plate".equals(name))return "\"River Plate\" Argentina fútbol";
        if("Racing Club".equals(name))return "\"Racing Club de Avellaneda\"";
        String country=AppStore.countryForClub(name);
        return "\""+name+"\" fútbol"+(country==null?"":" "+country);
    }

    private boolean isRelevant(TeamQuery team,String title,String source){
        String value=normalize(title+" "+source);
        if(team.national)return value.contains(normalize(team.name))&&(value.contains("seleccion")||value.contains("futbol")||value.contains("mundial")||value.contains("eliminatoria")||value.contains("amistoso"));
        if("Liverpool (Uruguay)".equals(team.name))return value.contains("liverpool")&&(value.contains("uruguay")||value.contains("montevideo")||value.contains("belvedere"))&&!value.contains("premier league");
        if("Liverpool".equals(team.name))return value.contains("liverpool")&&(value.contains("premier")||value.contains("anfield")||value.contains("england")||value.contains("ingles"));
        if("Inter".equals(team.name))return value.contains("inter")&&(value.contains("milan")||value.contains("serie a")||value.contains("italia"));
        if("Milan".equals(team.name))return (value.contains("ac milan")||value.contains("milan"))&&(value.contains("serie a")||value.contains("italia")||value.contains("champions"));
        if("Nacional".equals(team.name))return value.contains("nacional")&&(value.contains("uruguay")||value.contains("tricolor")||value.contains("parque central"));
        return value.contains(normalize(team.name));
    }

    private String cleanTitle(String title,String source){
        if(source!=null&&!source.trim().isEmpty()){
            String suffix=" - "+source.trim();
            if(title.endsWith(suffix))return title.substring(0,title.length()-suffix.length()).trim();
        }
        return title.trim();
    }

    private long parseDate(String value){
        String[]patterns={"EEE, dd MMM yyyy HH:mm:ss z","EEE, d MMM yyyy HH:mm:ss z"};
        for(String pattern:patterns)try{SimpleDateFormat format=new SimpleDateFormat(pattern,Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));Date date=format.parse(value);if(date!=null)return date.getTime();}catch(Exception ignored){}
        return -1L;
    }

    private boolean expiredPreview(String title,long published){
        String n=normalize(title);
        boolean preview=n.contains("donde ver")||n.contains("como ver")||n.contains("donde mirar")||
            n.contains("a que hora")||n.contains("que canal")||n.contains("transmision en vivo")||
            n.contains("posibles alineaciones")||n.contains("probables alineaciones")||
            n.contains("alineaciones probables")||n.contains("posible once")||
            n.contains("once probable")||n.contains("formaciones probables")||
            n.contains("previa del partido")||n.contains("horario y tv")||
            n.contains("horario canal")||n.contains("como seguir en vivo")||
            n.contains("pronostico del partido");
        // El RSS no incluye la fecha del encuentro: caducidad prudente basada en publicación.
        return preview&&System.currentTimeMillis()-published>18L*60L*60L*1000L;
    }

    private boolean historicalHeadline(String title){
        String n=normalize(title);
        int year=java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\b(20[0-9]{2})\\b").matcher(n);
        while(m.find()){
            int mentioned=Integer.parseInt(m.group(1));
            // Un año antiguo por sí solo puede ser contexto de una noticia nueva.
            // Solo descartar si el titular se presenta como archivo o efeméride.
            if(mentioned<year-1&&(n.contains("archivo")||n.contains("hemeroteca")||
                n.contains("un dia como hoy")||n.contains("recordamos")||
                n.contains("hace anos")||n.contains("retro")||n.contains("historico")))return true;
        }
        return false;
    }

    private int priority(NewsItem item){
        String n=normalize(item.title);int score=0;
        if(n.contains("fichaje")||n.contains("traspaso")||n.contains("lesion")||n.contains("sancion")||n.contains("renovacion")||n.contains("convocados")||n.contains("entrenador")||n.contains("tecnico")||n.contains("declaraciones"))score+=3;
        if(n.contains("donde ver")||n.contains("como ver")||n.contains("a que hora")||n.contains("posibles alineaciones")||n.contains("probables alineaciones")||n.contains("en vivo")||n.contains("pronostico")||n.contains("apuestas"))score-=3;
        return score;
    }

    private boolean excludedNews(String title,String source){
        String n=normalize(title);
        String src=normalize(source);
        if(n.contains("femenin")||n.contains("women")||n.contains("womens")||
           n.contains("femeni")||n.contains("liga f ")||n.contains("world cup women")||
           n.contains("sub 19")||n.contains("sub19")||n.contains("sub 17")||
           n.contains("sub17")||n.contains("u19 ")||n.contains("u17 ")||
           n.contains("juvenil femenino"))return true;
        // Los marcadores y fichas automáticas no son artículos periodísticos.
        if(n.matches(".*\\\\bvs?\\\\.?\\\\s+.*") &&
           (src.contains("tudn")||src.contains("espn")) &&
           !n.contains("analisis")&&!n.contains("cronica")&&!n.contains("goles")&&
           !n.contains("resumen")&&!n.contains("victoria")&&!n.contains("derrota"))return true;
        return false;
    }

    private boolean sameStory(NewsItem first,NewsItem second){
        String a=normalize(first.title),b=normalize(second.title);
        if(a.equals(b))return true;
        if(Math.abs(first.publishedAt-second.publishedAt)>48L*60L*60L*1000L)return false;
        Set<String> wordsA=new HashSet<>(),wordsB=new HashSet<>();
        for(String word:a.split(" "))if(word.length()>3)wordsA.add(word);
        for(String word:b.split(" "))if(word.length()>3)wordsB.add(word);
        if(wordsA.size()<5||wordsB.size()<5)return false;
        int shared=0;for(String word:wordsA)if(wordsB.contains(word))shared++;
        return shared>=5&&shared*1.0/Math.max(wordsA.size(),wordsB.size())>=0.8;
    }

    private List<NewsItem> dedupe(List<NewsItem> values){
        Collections.sort(values,(a,b)->{int diff=Integer.compare(priority(b),priority(a));return diff!=0?diff:Long.compare(b.publishedAt,a.publishedAt);});
        LinkedHashMap<String,List<NewsItem>> byTeam=new LinkedHashMap<>();
        List<NewsItem> globallySelected=new ArrayList<>();
        for(NewsItem item:values){
            if(item.publishedAt<=0||item.publishedAt<System.currentTimeMillis()-MAX_AGE_MS||
                item.publishedAt>System.currentTimeMillis()+60L*60L*1000L||
                expiredPreview(item.title,item.publishedAt)||historicalHeadline(item.title)||excludedNews(item.title,item.source)||
                item.title.isEmpty()||item.link.isEmpty())continue;
            List<NewsItem> teamItems=byTeam.get(item.team);
            if(teamItems==null){teamItems=new ArrayList<>();byTeam.put(item.team,teamItems);}
            boolean duplicate=false;
            for(NewsItem existing:globallySelected)if(sameStory(item,existing)){duplicate=true;break;}
            if(!duplicate&&teamItems.size()<MAX_PER_TEAM){teamItems.add(item);globallySelected.add(item);}
        }
        List<String> teams=new ArrayList<>(byTeam.keySet());
        Collections.sort(teams,String.CASE_INSENSITIVE_ORDER);
        List<NewsItem> balanced=new ArrayList<>();
        for(int round=0;round<MAX_PER_TEAM&&balanced.size()<MAX_TOTAL;round++){
            List<NewsItem> batch=new ArrayList<>();
            for(String team:teams){
                List<NewsItem> articles=byTeam.get(team);
                if(round<articles.size())batch.add(articles.get(round));
            }
            Collections.sort(batch,(a,b)->{int diff=Integer.compare(priority(b),priority(a));return diff!=0?diff:Long.compare(b.publishedAt,a.publishedAt);});
            for(NewsItem item:batch){if(balanced.size()>=MAX_TOTAL)break;balanced.add(item);}
        }
        return balanced;
    }

    private String normalize(String value){
        return Normalizer.normalize(value==null?"":value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
    }

    private List<NewsItem> readCache(){
        List<NewsItem> result=new ArrayList<>();
        try{JSONArray array=new JSONArray(prefs.getString("news_cache","[]"));for(int i=0;i<array.length();i++){NewsItem item=NewsItem.fromJson(array.getJSONObject(i));if(item.publishedAt>0 && item.publishedAt>=System.currentTimeMillis()-MAX_AGE_MS && item.publishedAt<=System.currentTimeMillis()+60L*60L*1000L && !expiredPreview(item.title,item.publishedAt) && !historicalHeadline(item.title) && !excludedNews(item.title,item.source))result.add(item);}}catch(Exception ignored){}
        return dedupe(result);
    }

    private void saveCache(List<NewsItem> items){
        JSONArray array=new JSONArray();
        try{for(NewsItem item:items)array.put(item.toJson());}catch(Exception ignored){}
        prefs.edit().putString("news_cache",array.toString()).putLong("news_updated_at",System.currentTimeMillis()).apply();
    }

    private static final class TeamQuery {
        final String name;final boolean national;
        TeamQuery(String name,boolean national){this.name=name;this.national=national;}
    }
}
