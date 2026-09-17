package uy.federico.mispartidos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class ApiClient {
    interface Callback { void done(boolean ok, String message); }
    interface TeamSearchCallback { void done(List<TeamOption> teams, String error); }
    interface LeagueCallback { void done(List<LeagueOption> leagues, String error); }
    static class TeamOption {
        final String id,name,country;final int importance;
        TeamOption(String id,String name,String country){this(id,name,country,0);}
        TeamOption(String id,String name,String country,int importance){this.id=id;this.name=name;this.country=country;this.importance=importance;}
        String label(){return country==null||country.isEmpty()?name:name+" · "+country;}
    }
    static class LeagueOption {
        final String id,name,season;final int popularity;
        LeagueOption(String id,String name,String season){this(id,name,season,0);}
        LeagueOption(String id,String name,String season,int popularity){this.id=id;this.name=name;this.season=season;this.popularity=popularity;}
        String label(){return season==null||season.isEmpty()?name:name+" · "+season;}
    }
    private static String proxyUrl="",proxyToken="";

    static boolean configured(Context context) {
        AppStore store=new AppStore(context);
        return !store.proxyUrl().isEmpty()&&!store.proxyToken().isEmpty();
    }

    static void sync(Context context, boolean force, Callback callback) {
        AppStore store=new AppStore(context);
        if(!configured(context)){callback.done(false,"Falta configurar la conexión");return;}
        proxyUrl=store.proxyUrl();proxyToken=store.proxyToken();
        if(!force&&!store.needsApiSync()){callback.done(true,"Datos actualizados");return;}
        new Thread(()->{
            try{
                Map<Long,Match> favorites=new ConcurrentHashMap<>();
                Map<Long,Match> previousFavorites=toMap(store.apiFavoriteMatches());
                List<String> failures=Collections.synchronizedList(new ArrayList<>()),reasons=Collections.synchronizedList(new ArrayList<>());
                List<Match> today=new ArrayList<>();
                String date=new SimpleDateFormat("yyyy-MM-dd",new Locale("es","UY")).format(new Date());
                Set<String> clubCompetitions=store.selectedClubCompetitions();
                Set<String> nationalCompetitions=store.selectedNationalCompetitions();
                if(!clubCompetitions.isEmpty()||!nationalCompetitions.isEmpty()){
                    try{
                        JSONObject response=request("fixturesByDate",params("date",date,"limit","100"));
                        today=parseToday(response,clubCompetitions,nationalCompetitions);
                    }catch(Exception e){failures.add("partidos de hoy");reasons.add(e.getMessage());}
                }

                int teamCount=store.selectedTeams().size()+store.selectedNationalTeams().size();
                if(teamCount>0){
                    ExecutorService pool=Executors.newFixedThreadPool(Math.min(4,teamCount));
                    AtomicBoolean quotaReached=new AtomicBoolean(false);
                    for(String team:store.selectedTeams())pool.submit(()->loadTeamSafe(store,team,false,favorites,previousFavorites,failures,reasons,quotaReached));
                    for(String team:store.selectedNationalTeams())pool.submit(()->loadTeamSafe(store,team,true,favorites,previousFavorites,failures,reasons,quotaReached));
                    pool.shutdown();
                    if(!pool.awaitTermination(90,TimeUnit.SECONDS))pool.shutdownNow();
                }

                if(favorites.isEmpty()&&!failures.isEmpty())favorites.putAll(previousFavorites);
                if(today.isEmpty()&&failures.contains("partidos de hoy"))today=store.apiTodayMatches();
                List<Match> favoriteList=new ArrayList<>(favorites.values());
                favoriteList.sort((a,b)->Long.compare(a.kickoff,b.kickoff));
                store.saveApiMatches(favoriteList,today);
                String message=failures.isEmpty()?"Actualizado ahora":"Actualizado con datos pendientes de "+failures.size()+(failures.size()==1?" elemento":" elementos");
                new Handler(Looper.getMainLooper()).post(()->callback.done(true,message));
            }catch(Exception e){
                new Handler(Looper.getMainLooper()).post(()->callback.done(false,"No se pudo actualizar: "+e.getMessage()));
            }
        }).start();
    }

    private static void loadTeamSafe(AppStore store,String team,boolean national,Map<Long,Match> favorites,Map<Long,Match> previous,List<String> failures,List<String> reasons,AtomicBoolean quotaReached){
        if(quotaReached.get()){addPreviousTeam(previous,team,favorites);return;}
        try{loadTeam(store,team,national,favorites);}
        catch(Exception e){failures.add(team);reasons.add(e.getMessage());addPreviousTeam(previous,team,favorites);if(isQuotaError(e.getMessage()))quotaReached.set(true);}
    }

    private static void loadTeam(AppStore store,String selectedName,boolean national,Map<Long,Match> out)throws Exception{
        String cacheKey="goal:"+(national?"N:":"C:")+selectedName;
        String teamId=resolveTeamId(store,selectedName,national,cacheKey,false);
        if(teamId==null||teamId.isEmpty())throw new Exception("No se encontró el equipo en GOAL API");
        JSONArray fixtures;
        try{fixtures=apiData(request("teamUpcoming",params("team",teamId,"limit","3")));}
        catch(Exception e){if(!String.valueOf(e.getMessage()).contains("404"))throw e;store.clearApiTeamId(cacheKey);teamId=resolveTeamId(store,selectedName,national,cacheKey,true);if(teamId==null||teamId.isEmpty())throw e;fixtures=apiData(request("teamUpcoming",params("team",teamId,"limit","3")));}
        for(Match match:parseUpcoming(fixtures,selectedName,teamId))out.put(match.id,match);
    }

    private static String resolveTeamId(AppStore store,String selectedName,boolean national,String cacheKey,boolean force)throws Exception{
        String teamId=force?null:store.apiTeamId(cacheKey);if(teamId!=null&&!teamId.isEmpty())return teamId;
        String searchName=national?apiCountryName(selectedName):apiSearchName(selectedName);
        Map<String,String> search=params("search",searchName,"limit","20");String savedCountry=store.apiTeamCountry(selectedName);String country=national?apiCountryName(selectedName):apiCountryName(savedCountry!=null?savedCountry:AppStore.countryForClub(selectedName));if(country!=null&&!country.isEmpty())search.put("country",country);
        JSONArray teams=apiData(request("teams",search));JSONObject selected=selectTeam(teams,selectedName,country,national);if(selected!=null){teamId=selected.optString("id",null);if(teamId!=null)store.saveApiTeamId(cacheKey,teamId);}return teamId;
    }

    static void searchTeams(Context context,String query,TeamSearchCallback callback){
        AppStore store=new AppStore(context);
        if(!configured(context)){callback.done(new ArrayList<>(),"Falta configurar la conexión");return;}
        proxyUrl=store.proxyUrl();proxyToken=store.proxyToken();
        new Thread(()->{
            List<TeamOption> result=new ArrayList<>();String error=null;
            try{
                JSONArray data=apiData(request("teams",params("search",query.trim(),"limit","50")));
                for(int i=0;i<data.length();i++){
                    JSONObject item=data.optJSONObject(i);if(item==null)continue;
                    String id=item.optString("id"),name=item.optString("name"),country=item.isNull("country")?"":item.optString("country");
                    if(!id.isEmpty()&&!name.isEmpty())result.add(new TeamOption(id,name,country,teamImportance(item)));
                }
            }catch(Exception e){error=e.getMessage();}
            String finalError=error;new Handler(Looper.getMainLooper()).post(()->callback.done(result,finalError));
        }).start();
    }

    static void countryLeagues(Context context,String country,LeagueCallback callback){
        prepare(context);new Thread(()->{List<LeagueOption>result=new ArrayList<>();String error=null;try{
            JSONArray data=apiData(request("countryLeagues",params("country",apiCountryName(country))));
            for(int i=0;i<data.length();i++){JSONObject x=data.optJSONObject(i);if(x==null)continue;String id=x.optString("id"),name=x.optString("name"),season=x.optString("season");if(!id.isEmpty()&&!name.isEmpty())result.add(new LeagueOption(id,name,season,x.optInt("popularity",0)));}
            List<LeagueOption>unique=dedupeLeagues(result);result.clear();result.addAll(unique);sortLeagues(result);saveLeaguesCache(context,country,result);
        }catch(Exception e){error=e.getMessage();}String finalError=error;new Handler(Looper.getMainLooper()).post(()->callback.done(result,finalError));}).start();
    }

    static void leagueTeams(Context context,String leagueId,TeamSearchCallback callback){
        prepare(context);new Thread(()->{List<TeamOption>result=new ArrayList<>();String error=null;try{
            JSONArray data=apiData(request("leagueTeams",params("league",leagueId,"limit","100")));
            for(int i=0;i<data.length();i++){JSONObject x=data.optJSONObject(i);if(x==null)continue;String id=x.optString("id"),name=x.optString("name"),country=x.isNull("country")?"":x.optString("country");if(!id.isEmpty()&&!name.isEmpty())result.add(new TeamOption(id,name,country,teamImportance(x)));}
            List<TeamOption>unique=dedupeTeams(result);result.clear();result.addAll(unique);sortTeams(result);saveTeamsCache(context,leagueId,result);
        }catch(Exception e){error=e.getMessage();}String finalError=error;new Handler(Looper.getMainLooper()).post(()->callback.done(result,finalError));}).start();
    }

    private static void prepare(Context context){AppStore s=new AppStore(context);proxyUrl=s.proxyUrl();proxyToken=s.proxyToken();}
    static List<LeagueOption> cachedCountryLeagues(Context context,String country){List<LeagueOption>r=new ArrayList<>();try{JSONArray a=new JSONArray(catalogPrefs(context).getString("leagues_"+normalize(country),"[]"));for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);r.add(new LeagueOption(x.getString("id"),x.getString("name"),x.optString("season"),x.optInt("popularity",0)));}}catch(Exception ignored){}r=dedupeLeagues(r);sortLeagues(r);return r;}
    static List<TeamOption> cachedLeagueTeams(Context context,String leagueId){List<TeamOption>r=new ArrayList<>();try{JSONArray a=new JSONArray(catalogPrefs(context).getString("teams_"+leagueId,"[]"));for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);r.add(new TeamOption(x.getString("id"),x.getString("name"),x.optString("country"),x.optInt("importance",0)));}}catch(Exception ignored){}r=dedupeTeams(r);sortTeams(r);return r;}
    private static void saveLeaguesCache(Context context,String country,List<LeagueOption>values){try{JSONArray a=new JSONArray();for(LeagueOption x:values){JSONObject o=new JSONObject();o.put("id",x.id);o.put("name",x.name);o.put("season",x.season);o.put("popularity",x.popularity);a.put(o);}catalogPrefs(context).edit().putString("leagues_"+normalize(country),a.toString()).apply();}catch(Exception ignored){}}
    private static void saveTeamsCache(Context context,String leagueId,List<TeamOption>values){try{JSONArray a=new JSONArray();for(TeamOption x:values){JSONObject o=new JSONObject();o.put("id",x.id);o.put("name",x.name);o.put("country",x.country);o.put("importance",x.importance);a.put(o);}catalogPrefs(context).edit().putString("teams_"+leagueId,a.toString()).apply();}catch(Exception ignored){}}
    private static SharedPreferences catalogPrefs(Context context){return context.getSharedPreferences("goal_catalog",Context.MODE_PRIVATE);}
    private static List<TeamOption> dedupeTeams(List<TeamOption>values){Map<String,TeamOption>unique=new LinkedHashMap<>();for(TeamOption x:values){String key=normalize(x.name),oldKey=key;TeamOption old=unique.get(oldKey);if(old==null||x.importance>old.importance)unique.put(key,x);}return new ArrayList<>(unique.values());}
    private static List<LeagueOption> dedupeLeagues(List<LeagueOption>values){Map<String,LeagueOption>unique=new LinkedHashMap<>();for(LeagueOption x:values){String key=normalize(x.name),season=x.season==null?"":x.season;LeagueOption old=unique.get(key);if(old==null||season.compareTo(old.season==null?"":old.season)>0)unique.put(key,x);}return new ArrayList<>(unique.values());}
    private static int teamImportance(JSONObject x){JSONObject count=x.optJSONObject("_count");return count==null?0:count.optInt("homeFixtures",0)+count.optInt("awayFixtures",0)+count.optInt("players",0);}
    private static void sortTeams(List<TeamOption>values){values.sort((a,b)->{int c=Integer.compare(b.importance,a.importance);return c!=0?c:a.name.compareToIgnoreCase(b.name);});}
    private static void sortLeagues(List<LeagueOption>values){values.sort((a,b)->{int c=Integer.compare(b.popularity,a.popularity);if(c!=0)return c;c=Integer.compare(leagueRank(a.name),leagueRank(b.name));return c!=0?c:a.name.compareToIgnoreCase(b.name);});}
    private static int leagueRank(String name){String n=normalize(name);if(n.contains("premier league")||n.equals("la liga")||n.equals("serie a")||n.equals("bundesliga")||n.equals("ligue 1")||n.contains("primeira liga")||n.contains("primera division"))return 0;if(n.contains("segunda")||n.contains("ligue 2")||n.contains("serie b")||n.contains("championship")||n.contains("2 bundesliga"))return 1;return 2;}

    private static JSONObject selectTeam(JSONArray teams,String selectedName,String expectedCountry,boolean national){
        JSONObject fallback=null;String wanted=normalize(national?apiCountryName(selectedName):apiSearchName(selectedName));
        for(int i=0;i<teams.length();i++){
            JSONObject team=teams.optJSONObject(i);if(team==null)continue;
            String name=normalize(team.optString("name")),country=canonicalCountry(team.optString("country"));
            boolean rightCountry=expectedCountry==null||canonicalCountry(expectedCountry).equals(country);
            if(fallback==null&&rightCountry)fallback=team;
            if(rightCountry&&(name.equals(wanted)||name.contains(wanted)||wanted.contains(name)))return team;
        }
        return fallback;
    }

    private static List<Match> parseUpcoming(JSONArray data,String selectedName,String selectedId)throws Exception{
        List<Match> result=new ArrayList<>();long now=System.currentTimeMillis();
        for(int i=0;i<data.length();i++){
            JSONObject f=data.getJSONObject(i);long kickoff=parseKickoff(f);if(kickoff<=now)continue;
            String homeId=f.optString("homeTeamId"),awayId=f.optString("awayTeamId");
            String home=nameOf(f,"homeTeam","homeTeamName"),away=nameOf(f,"awayTeam","awayTeamName");
            String opponent=selectedId.equals(homeId)?away:selectedId.equals(awayId)?home:(normalize(home).equals(normalize(selectedName))?away:home);
            result.add(new Match(matchId(f),selectedName,opponent,competitionName(f),kickoff,false));
        }
        result.sort((a,b)->Long.compare(a.kickoff,b.kickoff));
        return result.isEmpty()?result:new ArrayList<>(result.subList(0,1));
    }

    private static List<Match> parseToday(JSONObject wrapper,Set<String> clubCups,Set<String> nationalCups)throws Exception{
        JSONArray data=apiData(wrapper);List<Match> result=new ArrayList<>();long cutoff=System.currentTimeMillis()-7_200_000L;
        for(int i=0;i<data.length();i++){
            JSONObject f=data.getJSONObject(i);String competition=competitionName(f),country=f.optString("countryName");
            if(!competitionSelected(competition,country,clubCups,nationalCups))continue;
            long kickoff=parseKickoff(f);if(kickoff<cutoff)continue;
            result.add(new Match(matchId(f),nameOf(f,"homeTeam","homeTeamName"),nameOf(f,"awayTeam","awayTeamName"),competition,kickoff,false));
        }
        result.sort((a,b)->Long.compare(a.kickoff,b.kickoff));return result;
    }

    private static String nameOf(JSONObject fixture,String objectKey,String flatKey){
        JSONObject nested=fixture.optJSONObject(objectKey);
        return nested==null?fixture.optString(flatKey,"Equipo"):nested.optString("name",fixture.optString(flatKey,"Equipo"));
    }

    private static String competitionName(JSONObject fixture){
        JSONObject league=fixture.optJSONObject("league");
        String base=league==null?fixture.optString("leagueName","Partido"):league.optString("name",fixture.optString("leagueName","Partido"));
        String stage=fixture.optString("stageName");
        if(!stage.isEmpty()&&!stage.equalsIgnoreCase("Current")&&!normalize(base).contains(normalize(stage)))return base+" · "+stage;
        return base;
    }

    private static long parseKickoff(JSONObject fixture)throws Exception{
        String iso=fixture.optString("kickoffUtc");
        if(iso.isEmpty())throw new Exception("Un partido no tiene fecha");
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date parsed=format.parse(iso);if(parsed==null)throw new Exception("Fecha de partido inválida");return parsed.getTime();
    }

    private static long matchId(JSONObject fixture){
        String apiId=fixture.optString("apiId");
        try{return Long.parseLong(apiId);}catch(Exception ignored){}
        String id=fixture.optString("id",fixture.toString());long value=1125899906842597L;
        for(int i=0;i<id.length();i++)value=31*value+id.charAt(i);return value==Long.MIN_VALUE?0:Math.abs(value);
    }

    private static boolean competitionSelected(String apiName,String apiCountry,Set<String> clubCups,Set<String> nationalCups){
        Set<String> all=new HashSet<>();if(clubCups!=null)all.addAll(clubCups);if(nationalCups!=null)all.addAll(nationalCups);
        String actualName=canonicalCompetition(apiName),actualCountry=canonicalCountry(apiCountry);
        for(String full:all){
            String[] parts=full.split("›");String expectedName=canonicalCompetition(AppStore.shortName(full));
            String expectedScope=parts.length>1?canonicalCountry(parts[parts.length-2].trim()):"";
            if(hasDifferentCategory(expectedName,actualName))continue;
            if(expectedName.equals("eliminatorias")){
                boolean qualification=actualName.contains("world cup")&&actualName.contains("qualification");
                if(expectedScope.equals("conmebol")&&qualification&&(actualName.contains("conmebol")||actualName.contains("south america")))return true;
                if(expectedScope.equals("uefa")&&qualification&&(actualName.contains("uefa")||actualName.contains("europe")))return true;
                continue;
            }
            if(!expectedName.equals(actualName))continue;
            if(expectedScope.equals("uefa")||expectedScope.equals("conmebol")||expectedScope.equals("fifa"))return actualCountry.equals("intl")||actualCountry.equals("world")||actualName.contains(expectedScope);
            if(expectedScope.equals(actualCountry))return true;
        }
        return false;
    }

    private static boolean hasDifferentCategory(String expected,String actual){
        String[] markers={"women","woman","female","u17","u18","u19","u20","u21","u23","youth","reserve","reserves","futsal","beach","amateur"};
        for(String marker:markers)if(actual.contains(marker)&&!expected.contains(marker))return true;
        return false;
    }

    private static String canonicalCompetition(String value){
        String n=normalize(value).replace(" clausura","").replace(" apertura","").replace(" group stage","");
        if(n.equals("brasileirao"))return"serie a";if(n.equals("liga profesional"))return"liga profesional argentina";
        if(n.equals("copa auf uruguay"))return"copa uruguay";if(n.equals("laliga"))return"la liga";
        if(n.equals("supercopa de espana"))return"super cup";if(n.equals("efl cup"))return"league cup";
        if(n.equals("champions league"))return"uefa champions league";if(n.equals("europa league"))return"uefa europa league";
        if(n.equals("conference league"))return"uefa europa conference league";if(n.equals("nations league"))return"uefa nations league";
        if(n.equals("eurocopa"))return"uefa european championship";if(n.equals("copa del mundo"))return"world cup";
        if(n.equals("mundial de clubes"))return"fifa club world cup";if(n.equals("amistosos internacionales"))return"friendlies";
        if(n.equals("copa libertadores"))return"conmebol libertadores";if(n.equals("copa sudamericana"))return"conmebol sudamericana";
        if(n.equals("fifa world cup"))return"world cup";if(n.equals("conmebol copa america"))return"copa america";
        return n;
    }

    private static String apiSearchName(String selected){
        String value=selected.equals("Liverpool (Uruguay)")?"Liverpool Montevideo":selected.replace(" (Uruguay)","");
        Map<String,String> translations=new LinkedHashMap<>();
        translations.put("Países Bajos","Netherlands");translations.put("Corea del Sur","South Korea");translations.put("Estados Unidos","USA");
        translations.put("Alemania","Germany");translations.put("España","Spain");translations.put("Inglaterra","England");translations.put("Francia","France");
        translations.put("Marruecos","Morocco");translations.put("Japón","Japan");translations.put("Bélgica","Belgium");translations.put("Croacia","Croatia");
        translations.put("Brasil","Brazil");translations.put("PSG","Paris Saint-Germain");translations.put("Bayern Múnich","Bayern Munich");
        translations.put("Olympique de Marsella","Marseille");translations.put("Atlético de Madrid","Atletico Madrid");
        return translations.containsKey(value)?translations.get(value):value;
    }

    private static String apiCountryName(String value){
        if(value==null)return null;String canonical=canonicalCountry(value);
        if(canonical.equals("spain"))return"Spain";if(canonical.equals("england"))return"England";if(canonical.equals("france"))return"France";
        if(canonical.equals("germany"))return"Germany";if(canonical.equals("italy"))return"Italy";if(canonical.equals("netherlands"))return"Netherlands";
        if(canonical.equals("brazil"))return"Brazil";if(canonical.equals("south korea"))return"South Korea";if(canonical.equals("usa"))return"USA";
        if(canonical.equals("peru"))return"Peru";if(canonical.equals("mexico"))return"Mexico";if(canonical.equals("belgica"))return"Belgium";
        if(canonical.equals("escocia"))return"Scotland";if(canonical.equals("turquia"))return"Turkey";if(canonical.equals("grecia"))return"Greece";
        if(canonical.equals("suiza"))return"Switzerland";if(canonical.equals("dinamarca"))return"Denmark";if(canonical.equals("noruega"))return"Norway";
        if(canonical.equals("suecia"))return"Sweden";if(canonical.equals("polonia"))return"Poland";if(canonical.equals("republica checa"))return"Czech Republic";
        if(canonical.equals("rumania"))return"Romania";if(canonical.equals("sudafrica"))return"South Africa";if(canonical.equals("egipto"))return"Egypt";
        if(canonical.equals("argelia"))return"Algeria";if(canonical.equals("tunez"))return"Tunisia";if(canonical.equals("china"))return"China";
        if(canonical.equals("india"))return"India";if(canonical.equals("arabia saudita"))return"Saudi Arabia";if(canonical.equals("catar"))return"Qatar";
        if(canonical.equals("emiratos arabes unidos"))return"United Arab Emirates";if(canonical.equals("iran"))return"Iran";if(canonical.equals("nueva zelanda"))return"New Zealand";
        if(canonical.equals("albania"))return"Albania";if(canonical.equals("bosnia y herzegovina"))return"Bosnia and Herzegovina";
        if(canonical.equals("bulgaria"))return"Bulgaria";if(canonical.equals("eslovaquia"))return"Slovakia";if(canonical.equals("eslovenia"))return"Slovenia";
        if(canonical.equals("finlandia"))return"Finland";if(canonical.equals("gales"))return"Wales";if(canonical.equals("georgia"))return"Georgia";
        if(canonical.equals("hungria"))return"Hungary";if(canonical.equals("irlanda"))return"Ireland";if(canonical.equals("irlanda del norte"))return"Northern Ireland";
        if(canonical.equals("islandia"))return"Iceland";if(canonical.equals("serbia"))return"Serbia";if(canonical.equals("ucrania"))return"Ukraine";
        if(canonical.equals("costa de marfil"))return"Ivory Coast";if(canonical.equals("camerun"))return"Cameroon";if(canonical.equals("ghana"))return"Ghana";
        if(canonical.equals("jamaica"))return"Jamaica";if(canonical.equals("panama"))return"Panama";if(canonical.equals("iraq"))return"Iraq";
        return value;
    }

    private static String canonicalCountry(String value){
        String n=normalize(value);if(n.equals("espana"))return"spain";if(n.equals("inglaterra"))return"england";if(n.equals("francia"))return"france";
        if(n.equals("alemania"))return"germany";if(n.equals("italia"))return"italy";if(n.equals("paises bajos"))return"netherlands";
        if(n.equals("brasil"))return"brazil";if(n.equals("estados unidos"))return"usa";if(n.equals("corea del sur"))return"south korea";
        if(n.equals("mundo"))return"world";return n;
    }

    private static JSONArray apiData(JSONObject wrapper)throws Exception{
        if(!wrapper.optBoolean("ok"))throw new Exception(wrapper.optString("error","Error del intermediario"));
        JSONObject response=wrapper.optJSONObject("data");if(response==null)throw new Exception("Respuesta incompleta del intermediario");
        if(!response.optBoolean("success",true))throw new Exception(response.optString("error","GOAL API rechazó la consulta"));
        JSONArray data=response.optJSONArray("data");return data==null?new JSONArray():data;
    }

    private static JSONObject request(String action,Map<String,String> values)throws Exception{
        IOException last=null;for(int attempt=0;attempt<3;attempt++){try{return requestOnce(action,values);}catch(IOException e){last=e;if(attempt<2)try{Thread.sleep(700L*(attempt+1));}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}}
        throw new Exception("Sin conexión temporal. Probá actualizar nuevamente.",last);
    }

    private static JSONObject requestOnce(String action,Map<String,String> values)throws Exception{
        StringBuilder u=new StringBuilder(proxyUrl);u.append(proxyUrl.contains("?")?'&':'?').append("action=").append(enc(action)).append("&token=").append(enc(proxyToken));
        for(Map.Entry<String,String> e:values.entrySet())u.append('&').append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        HttpURLConnection c=(HttpURLConnection)new URL(u.toString()).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(true);
        int status=c.getResponseCode();InputStream stream=status>=200&&status<400?c.getInputStream():c.getErrorStream();
        BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));StringBuilder body=new StringBuilder();String line;
        while((line=reader.readLine())!=null)body.append(line);reader.close();c.disconnect();
        if(status<200||status>=400)throw new Exception("HTTP "+status);return new JSONObject(body.toString());
    }

    private static boolean containsQuotaError(List<String> reasons){for(String s:reasons)if(isQuotaError(s))return true;return false;}
    private static boolean isQuotaError(String s){String n=normalize(s==null?"":s);return n.contains("rate limit")||n.contains("too many requests")||n.contains("daily limit")||n.contains("quota");}
    private static String firstUsefulReason(List<String> reasons){for(String s:reasons)if(s!=null&&!s.trim().isEmpty())return s.length()>170?s.substring(0,170)+"…":s;return"";}
    private static Map<Long,Match> toMap(List<Match> list){Map<Long,Match> result=new LinkedHashMap<>();for(Match m:list)result.put(m.id,m);return result;}
    private static void addPreviousTeam(Map<Long,Match>previous,String team,Map<Long,Match>target){long now=System.currentTimeMillis();for(Match m:previous.values())if(m.kickoff>now&&normalize(m.team).equals(normalize(team)))target.put(m.id,m);}
    private static String normalize(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    private static Map<String,String> params(String... values){Map<String,String> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put(values[i],values[i+1]);return result;}
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
}
