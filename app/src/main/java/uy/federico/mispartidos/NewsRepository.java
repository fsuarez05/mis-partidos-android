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

final class NewsRepository {
    interface Callback { void onComplete(List<NewsItem> items,boolean updated,String message); }

    static final long CACHE_MS=4L*60L*60L*1000L;
    private static final int MAX_QUERIES=8;
    private static final int MAX_PER_TEAM=6;
    private static final int MAX_TOTAL=60;
    private static final long MAX_AGE_MS=7L*24L*60L*60L*1000L;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
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
        if(!force&&!cache.isEmpty()&&System.currentTimeMillis()-lastUpdate()<CACHE_MS){
            callback.onComplete(cache,false,"Noticias guardadas");
            return;
        }
        EXECUTOR.execute(()->refresh(cache,callback));
    }

    private void refresh(List<NewsItem> old,Callback callback){
        AppStore store=new AppStore(context);
        List<TeamQuery> all=new ArrayList<>();
        for(String team:store.selectedTeams())all.add(new TeamQuery(team,false));
        for(String team:store.selectedNationalTeams())all.add(new TeamQuery(team,true));
        Collections.sort(all,(a,b)->a.name.compareToIgnoreCase(b.name));
        if(all.isEmpty()){post(callback,old,false,"Elegí al menos un club o selección para ver noticias.");return;}

        int start=Math.floorMod(prefs.getInt("news_query_cursor",0),all.size());
        int count=Math.min(MAX_QUERIES,all.size());
        Set<String> refreshedTeams=new HashSet<>();
        List<NewsItem> downloaded=new ArrayList<>();
        List<String> failures=new ArrayList<>();
        for(int i=0;i<count;i++){
            TeamQuery team=all.get((start+i)%all.size());
            try{
                downloaded.addAll(fetch(team));
                refreshedTeams.add(team.name);
            }catch(Exception e){
                failures.add(team.name);
            }
        }
        prefs.edit().putInt("news_query_cursor",(start+count)%all.size()).apply();

        if(downloaded.isEmpty()){
            String message=old.isEmpty()?"No se pudieron obtener noticias. Revisá la conexión.":"No se pudo actualizar. Se muestran las noticias guardadas.";
            post(callback,old,false,message);
            return;
        }

        List<NewsItem> merged=new ArrayList<>(downloaded);
        for(NewsItem item:old)if(!refreshedTeams.contains(item.team))merged.add(item);
        merged=dedupe(merged);
        saveCache(merged);
        String message=failures.isEmpty()?"Noticias actualizadas":("Actualización parcial. Sin respuesta para "+failures.size()+" equipo"+(failures.size()==1?".":"s."));
        post(callback,merged,true,message);
    }

    private void post(Callback callback,List<NewsItem> items,boolean updated,String message){
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.onComplete(items,updated,message));
    }

    private List<NewsItem> fetch(TeamQuery team) throws Exception {
        String query=queryFor(team);
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
                    if(published>0 && published<=System.currentTimeMillis()+60L*60L*1000L && published>=System.currentTimeMillis()-MAX_AGE_MS && isRelevant(team,title,source) && !expiredPreview(title,published))result.add(new NewsItem(cleanTitle(title,source),source,link,team.name,published));
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
        boolean preview=n.contains("donde ver")||n.contains("como ver")||n.contains("a que hora")||n.contains("que canal")||n.contains("transmision en vivo")||n.contains("posibles alineaciones")||n.contains("probables alineaciones")||n.contains("formaciones probables")||n.contains("previa del partido")||n.contains("horario y tv");
        // Sin un vínculo fiable con el fixture, las previas vencen a las 24 horas.
        return preview && System.currentTimeMillis()-published>24L*60L*60L*1000L;
    }

    private List<NewsItem> dedupe(List<NewsItem> values){
        Collections.sort(values,(a,b)->Long.compare(b.publishedAt,a.publishedAt));
        LinkedHashMap<String,NewsItem> unique=new LinkedHashMap<>();
        for(NewsItem item:values){
            if(item.publishedAt<=0 || item.publishedAt<System.currentTimeMillis()-MAX_AGE_MS || item.publishedAt>System.currentTimeMillis()+60L*60L*1000L || expiredPreview(item.title,item.publishedAt))continue;
            String key=normalize(item.title).replaceAll("\\b(el|la|los|las|un|una|de|del|y|en)\\b","").replaceAll("\\s+"," ").trim();
            if(!key.isEmpty()&&!unique.containsKey(key))unique.put(key,item);
            if(unique.size()>=MAX_TOTAL)break;
        }
        return new ArrayList<>(unique.values());
    }

    private String normalize(String value){
        return Normalizer.normalize(value==null?"":value.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[^a-z0-9 ]"," ").replaceAll("\\s+"," ").trim();
    }

    private List<NewsItem> readCache(){
        List<NewsItem> result=new ArrayList<>();
        try{JSONArray array=new JSONArray(prefs.getString("news_cache","[]"));for(int i=0;i<array.length();i++){NewsItem item=NewsItem.fromJson(array.getJSONObject(i));if(item.publishedAt>0 && item.publishedAt>=System.currentTimeMillis()-MAX_AGE_MS && item.publishedAt<=System.currentTimeMillis()+60L*60L*1000L && !expiredPreview(item.title,item.publishedAt))result.add(item);}}catch(Exception ignored){}
        Collections.sort(result,(a,b)->Long.compare(b.publishedAt,a.publishedAt));return result;
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
