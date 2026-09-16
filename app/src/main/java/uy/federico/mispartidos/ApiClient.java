package uy.federico.mispartidos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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

class ApiClient {
    interface Callback { void done(boolean ok, String message); }
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
                Map<Long,Match> favorites=new LinkedHashMap<>();
                List<String> failures=new ArrayList<>(),reasons=new ArrayList<>();
                List<Match> today=new ArrayList<>();
                String date=new SimpleDateFormat("yyyy-MM-dd",new Locale("es","UY")).format(new Date());
                Set<String> clubCompetitions=store.selectedClubCompetitions();
                Set<String> nationalCompetitions=store.selectedNationalCompetitions();
                if(!clubCompetitions.isEmpty()||!nationalCompetitions.isEmpty()){
                    try{
                        JSONObject response=request("fixturesByDate",params("date",date,"limit","500"));
                        today=parseToday(response,clubCompetitions,nationalCompetitions);
                    }catch(Exception e){failures.add("partidos de hoy");reasons.add(e.getMessage());}
                }

                for(String team:store.selectedTeams()){
                    try{loadTeam(store,team,false,favorites);}
                    catch(Exception e){failures.add(team);reasons.add(e.getMessage());if(isQuotaError(e.getMessage()))break;}
                }
                if(!containsQuotaError(reasons))for(String team:store.selectedNationalTeams()){
                    try{loadTeam(store,team,true,favorites);}
                    catch(Exception e){failures.add(team);reasons.add(e.getMessage());if(isQuotaError(e.getMessage()))break;}
                }

                if(favorites.isEmpty()&&!failures.isEmpty())favorites=toMap(store.apiFavoriteMatches());
                if(today.isEmpty()&&failures.contains("partidos de hoy"))today=store.apiTodayMatches();
                List<Match> favoriteList=new ArrayList<>(favorites.values());
                favoriteList.sort((a,b)->Long.compare(a.kickoff,b.kickoff));
                store.saveApiMatches(favoriteList,today);
                String reason=firstUsefulReason(reasons);
                String message=failures.isEmpty()?"Actualizado ahora":"Actualización parcial: fallaron "+failures.size()+" consultas"+(reason.isEmpty()?"":". "+reason);
                new Handler(Looper.getMainLooper()).post(()->callback.done(true,message));
            }catch(Exception e){
                new Handler(Looper.getMainLooper()).post(()->callback.done(false,"No se pudo actualizar: "+e.getMessage()));
            }
        }).start();
    }

    private static void loadTeam(AppStore store,String selectedName,boolean national,Map<Long,Match> out)throws Exception{
        String teamId=null;
        Map<String,String> search=params("search",apiSearchName(selectedName),"limit","20");
        String country=national?apiCountryName(selectedName):apiCountryName(AppStore.countryForClub(selectedName));
        if(country!=null&&!country.isEmpty())search.put("country",country);
        JSONArray teams=apiData(request("teams",search));
        JSONObject selected=selectTeam(teams,selectedName,country,national);
        if(selected!=null)teamId=selected.optString("id",null);
        if(teamId==null||teamId.isEmpty())throw new Exception("No se encontró el equipo en GOAL API");
        JSONArray fixtures=apiData(request("teamUpcoming",params("team",teamId,"limit","20")));
        for(Match match:parseUpcoming(fixtures,selectedName,teamId))out.put(match.id,match);
    }

    private static JSONObject selectTeam(JSONArray teams,String selectedName,String expectedCountry,boolean national){
        JSONObject fallback=null;String wanted=normalize(apiSearchName(selectedName));
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
        return result.size()>10?new ArrayList<>(result.subList(0,10)):result;
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
            boolean nameMatches=expectedName.equals(actualName)||actualName.startsWith(expectedName+" ")||expectedName.startsWith(actualName+" ");
            if(expectedName.equals("eliminatorias")){
                if(expectedScope.equals("conmebol")&&(actualName.contains("world cup")||actualName.contains("qualification"))&&actualCountry.equals("intl"))return true;
                if(expectedScope.equals("uefa")&&(actualName.contains("world cup")||actualName.contains("qualification"))&&actualCountry.equals("intl"))return true;
                continue;
            }
            if(!nameMatches)continue;
            if(expectedScope.equals("uefa")||expectedScope.equals("conmebol")||expectedScope.equals("fifa"))return actualCountry.equals("intl")||actualCountry.equals("world")||actualName.contains(expectedScope);
            if(expectedScope.equals(actualCountry))return true;
        }
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
    private static String normalize(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
    private static Map<String,String> params(String... values){Map<String,String> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put(values[i],values[i+1]);return result;}
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
}
