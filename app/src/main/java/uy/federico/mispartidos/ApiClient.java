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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class ApiClient {
    interface Callback { void done(boolean ok, String message); }
    private static String proxyUrl="",proxyToken="";

    static boolean configured(Context context) {
        AppStore store=new AppStore(context);return !store.proxyUrl().isEmpty()&&!store.proxyToken().isEmpty();
    }

    static void sync(Context context, boolean force, Callback callback) {
        AppStore store = new AppStore(context);
        if (!configured(context)) { callback.done(false,"Falta configurar la conexión"); return; }
        proxyUrl=store.proxyUrl();proxyToken=store.proxyToken();
        if (!force && !store.needsApiSync()) { callback.done(true,"Datos actualizados"); return; }
        new Thread(() -> {
            try {
                Map<Long,Match> favorites = new LinkedHashMap<>();
                List<String> failures = new ArrayList<>();
                List<String> failureReasons = new ArrayList<>();
                boolean quotaBlocked=false;
                for (String team : store.selectedTeams()) {
                    try { loadTeamFixtures(store,team,false,favorites); }
                    catch (Exception e) { failures.add(team); failureReasons.add(e.getMessage()); if(isQuotaError(e.getMessage())){quotaBlocked=true;break;} }
                }
                if(!quotaBlocked)for (String team : store.selectedNationalTeams()) {
                    try { loadTeamFixtures(store,team,true,favorites); }
                    catch (Exception e) { failures.add(team); failureReasons.add(e.getMessage()); if(isQuotaError(e.getMessage())){quotaBlocked=true;break;} }
                }

                String date = new SimpleDateFormat("yyyy-MM-dd",new Locale("es","UY")).format(new Date());
                List<Match> today = new ArrayList<>();
                try {
                    JSONObject todayJson = request("fixturesByDate",params("date",date,"timezone","America/Montevideo"));
                    today = parseFixtures(todayJson,store.selectedClubCompetitions(),store.selectedNationalCompetitions(),true);
                } catch (Exception e) { failures.add("partidos de hoy"); failureReasons.add(e.getMessage()); }
                if(favorites.isEmpty()&&!failures.isEmpty())favorites=toMap(store.apiFavoriteMatches());
                if(today.isEmpty()&&failures.contains("partidos de hoy"))today=store.apiTodayMatches();
                store.saveApiMatches(new ArrayList<>(favorites.values()),today);
                String reason=firstUsefulReason(failureReasons);
                String message = failures.isEmpty() ? "Actualizado ahora" : "Actualización parcial: fallaron "+failures.size()+" consultas"+(reason.isEmpty()?"":". "+reason);
                new Handler(Looper.getMainLooper()).post(() -> callback.done(true,message));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.done(false,"No se pudo actualizar: "+e.getMessage()));
            }
        }).start();
    }

    private static void loadTeamFixtures(AppStore store,String selectedName, boolean national, Map<Long,Match> out) throws Exception {
        Integer teamId=store.apiTeamId((national?"N:":"C:")+selectedName);
        if(teamId==null){
            String search = apiSearchName(selectedName);
            JSONObject teamsJson = request("teams",params("search",search));
            JSONArray teams = apiResponse(teamsJson); Integer compatibleFallback = null;
            for (int i=0;i<teams.length();i++) {
                JSONObject team=teams.getJSONObject(i).getJSONObject("team");
                String name=team.optString("name"),country=team.optString("country");boolean isNational=team.optBoolean("national");
                if(compatibleFallback==null && isNational==national)compatibleFallback=team.getInt("id");
                if (matchesTeam(selectedName,name,country,national,isNational)) { teamId=team.getInt("id"); break; }
            }
            if(teamId==null)teamId=compatibleFallback;
            if(teamId!=null)store.saveApiTeamId((national?"N:":"C:")+selectedName,teamId);
        }
        if(teamId==null)return;
        JSONObject fixtures=request("teamFixtures",params("team",String.valueOf(teamId),"next","10","timezone","America/Montevideo"));
        for(Match m:parseFavoriteFixtures(fixtures,selectedName,teamId))out.put(m.id,m);
    }

    private static List<Match> parseFavoriteFixtures(JSONObject wrapper,String selectedName,int selectedId)throws Exception{
        JSONArray a=apiResponse(wrapper);List<Match>result=new ArrayList<>();
        for(int i=0;i<a.length();i++){
            JSONObject item=a.getJSONObject(i),fixture=item.getJSONObject("fixture"),league=item.getJSONObject("league"),teams=item.getJSONObject("teams");
            JSONObject homeTeam=teams.getJSONObject("home"),awayTeam=teams.getJSONObject("away");
            String opponent=homeTeam.getInt("id")==selectedId?awayTeam.getString("name"):homeTeam.getString("name");long kickoff=fixture.getLong("timestamp")*1000L;
            if(kickoff>System.currentTimeMillis())result.add(new Match(fixture.getLong("id"),selectedName,opponent,league.optString("name","Partido"),kickoff,false));
        }
        result.sort((x,y)->Long.compare(x.kickoff,y.kickoff));return result;
    }

    private static boolean matchesTeam(String selected,String apiName,String country,boolean national,boolean apiNational){
        if(national){
            String wanted=normalize(apiSearchName(selected));
            return apiNational&&(normalize(apiName).equals(wanted)||normalize(country).equals(wanted));
        }
        String wanted=normalize(apiSearchName(selected)),actual=normalize(apiName);
        if(!actual.equals(wanted)&&!(selected.equals("Liverpool (Uruguay)")&&actual.contains("liverpool")))return false;
        String expected=AppStore.countryForClub(selected);return expected==null||canonicalCountry(country).equals(canonicalCountry(expected));
    }

    private static String apiSearchName(String selected){
        String value=selected.equals("Liverpool (Uruguay)")?"Liverpool Montevideo":selected.replace(" (Uruguay)","");
        Map<String,String> translations=new LinkedHashMap<>();
        translations.put("Países Bajos","Netherlands");
        translations.put("Corea del Sur","South Korea");
        translations.put("Estados Unidos","USA");
        translations.put("Alemania","Germany");
        translations.put("España","Spain");
        translations.put("Inglaterra","England");
        translations.put("Francia","France");
        translations.put("Italia","Italy");
        translations.put("Marruecos","Morocco");
        translations.put("Japón","Japan");
        translations.put("Bélgica","Belgium");
        translations.put("Croacia","Croatia");
        translations.put("Brasil","Brazil");
        translations.put("PSG","Paris Saint Germain");
        translations.put("Bayern Múnich","Bayern Munich");
        translations.put("Olympique de Marsella","Marseille");
        if(translations.containsKey(value))value=translations.get(value);
        return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}","").replaceAll("[^A-Za-z0-9 ]+"," ").replaceAll("\\s+"," ").trim();
    }

    private static List<Match> parseFixtures(JSONObject wrapper,Set<String>clubCups,Set<String>nationalCups,boolean filter) throws Exception {
        JSONArray a=apiResponse(wrapper);List<Match> result=new ArrayList<>();
        for(int i=0;i<a.length();i++){
            JSONObject item=a.getJSONObject(i),fixture=item.getJSONObject("fixture"),league=item.getJSONObject("league"),teams=item.getJSONObject("teams");
            String competition=league.optString("name","Partido"),country=league.optString("country","");if(filter&&!competitionSelected(competition,country,clubCups,nationalCups))continue;
            JSONObject home=teams.getJSONObject("home"),away=teams.getJSONObject("away");
            long kickoff=fixture.getLong("timestamp")*1000L;if(kickoff<System.currentTimeMillis()-7_200_000L)continue;
            result.add(new Match(fixture.getLong("id"),home.getString("name"),away.getString("name"),competition,kickoff,false));
        }
        result.sort((x,y)->Long.compare(x.kickoff,y.kickoff));return result;
    }

    private static boolean competitionSelected(String apiName,String apiCountry,Set<String>clubCups,Set<String>nationalCups){
        Set<String>all=new HashSet<>();if(clubCups!=null)all.addAll(clubCups);if(nationalCups!=null)all.addAll(nationalCups);
        String actualName=canonicalCompetition(apiName),actualCountry=canonicalCountry(apiCountry);
        for(String full:all){
            String[]parts=full.split("›");String expectedName=canonicalCompetition(AppStore.shortName(full));
            String expectedScope=parts.length>1?canonicalCountry(parts[parts.length-2].trim()):"";
            if(expectedName.equals("eliminatorias")){
                String raw=normalize(apiName);
                if(expectedScope.equals("conmebol")&&raw.contains("south america"))return true;
                if(expectedScope.equals("uefa")&&raw.contains("europe"))return true;
                continue;
            }
            if(!expectedName.equals(actualName))continue;
            if(expectedScope.equals("uefa")||expectedScope.equals("conmebol")||expectedScope.equals("fifa"))return actualCountry.equals("world")||actualCountry.equals(expectedScope);
            if(expectedScope.equals(actualCountry))return true;
        }return false;
    }
    private static String canonicalCountry(String s){String n=normalize(s);if(n.equals("espana"))return"spain";if(n.equals("inglaterra"))return"england";if(n.equals("francia"))return"france";if(n.equals("alemania"))return"germany";if(n.equals("italia"))return"italy";if(n.equals("paises bajos"))return"netherlands";if(n.equals("brasil"))return"brazil";if(n.equals("mundo"))return"world";return n;}
    private static String canonicalCompetition(String s){String n=normalize(s);if(n.equals("brasileirao"))return"serie a";if(n.equals("liga profesional"))return"liga profesional argentina";if(n.equals("copa auf uruguay"))return"copa uruguay";if(n.equals("laliga"))return"la liga";if(n.equals("supercopa de espana"))return"super cup";if(n.equals("efl cup"))return"league cup";if(n.equals("champions league"))return"uefa champions league";if(n.equals("europa league"))return"uefa europa league";if(n.equals("conference league"))return"uefa europa conference league";if(n.equals("nations league"))return"uefa nations league";if(n.equals("eurocopa"))return"euro championship";if(n.equals("copa del mundo"))return"world cup";if(n.equals("mundial de clubes"))return"fifa club world cup";if(n.equals("amistosos internacionales"))return"friendlies";return n;}
    private static Map<Long,Match>toMap(List<Match>items){Map<Long,Match>r=new LinkedHashMap<>();for(Match m:items)r.put(m.id,m);return r;}
    private static String firstUsefulReason(List<String>reasons){for(String r:reasons)if(r!=null&&!r.trim().isEmpty())return r.length()>160?r.substring(0,160)+"…":r;return"";}
    private static boolean isQuotaError(String s){String n=normalize(s==null?"":s);return n.contains("request limit")||n.contains("requests limit")||n.contains("rate limit")||n.contains("too many requests")||n.contains("daily limit");}
    private static String normalize(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}

    private static JSONArray apiResponse(JSONObject wrapper)throws Exception{
        if(!wrapper.optBoolean("ok"))throw new Exception(wrapper.optString("error","Error del intermediario"));
        JSONObject data=wrapper.getJSONObject("data");JSONObject errors=data.optJSONObject("errors");if(errors!=null&&errors.length()>0)throw new Exception(errors.toString());
        return data.getJSONArray("response");
    }

    private static JSONObject request(String action,Map<String,String> values)throws Exception{
        StringBuilder u=new StringBuilder(proxyUrl);u.append(proxyUrl.contains("?")?'&':'?');u.append("action=").append(enc(action));u.append("&token=").append(enc(proxyToken));
        for(Map.Entry<String,String>e:values.entrySet())u.append('&').append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        HttpURLConnection c=(HttpURLConnection)new URL(u.toString()).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);
        int status=c.getResponseCode();InputStream stream=status>=200&&status<400?c.getInputStream():c.getErrorStream();BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();c.disconnect();
        if(status<200||status>=400)throw new Exception("HTTP "+status);return new JSONObject(b.toString());
    }
    private static Map<String,String>params(String...v){Map<String,String>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put(v[i],v[i+1]);return m;}
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
}
