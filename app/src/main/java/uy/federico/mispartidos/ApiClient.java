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

    static boolean configured() {
        return !BuildConfig.PROXY_URL.isEmpty() && !BuildConfig.PROXY_TOKEN.isEmpty();
    }

    static void sync(Context context, boolean force, Callback callback) {
        AppStore store = new AppStore(context);
        if (!configured()) { callback.done(false,"Falta configurar la conexión"); return; }
        if (!force && !store.needsApiSync()) { callback.done(true,"Datos actualizados"); return; }
        new Thread(() -> {
            try {
                Map<Long,Match> favorites = new LinkedHashMap<>();
                for (String team : store.selectedTeams()) loadTeamFixtures(team,false,favorites);
                for (String team : store.selectedNationalTeams()) loadTeamFixtures(team,true,favorites);

                String date = new SimpleDateFormat("yyyy-MM-dd",new Locale("es","UY")).format(new Date());
                JSONObject todayJson = request("fixturesByDate",params("date",date,"timezone","America/Montevideo"));
                List<Match> today = parseFixtures(todayJson,store.selectedClubCompetitions(),store.selectedNationalCompetitions(),true);
                store.saveApiMatches(new ArrayList<>(favorites.values()),today);
                new Handler(Looper.getMainLooper()).post(() -> callback.done(true,"Actualizado ahora"));
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.done(false,"No se pudo actualizar: "+e.getMessage()));
            }
        }).start();
    }

    private static void loadTeamFixtures(String selectedName, boolean national, Map<Long,Match> out) throws Exception {
        String search = selectedName.replace(" (Uruguay)","");
        JSONObject teamsJson = request("teams",params("search",search));
        JSONArray teams = apiResponse(teamsJson); Integer teamId = null;
        for (int i=0;i<teams.length();i++) {
            JSONObject team=teams.getJSONObject(i).getJSONObject("team");
            String name=team.optString("name"),country=team.optString("country");boolean isNational=team.optBoolean("national");
            if (matchesTeam(selectedName,name,country,national,isNational)) { teamId=team.getInt("id"); break; }
        }
        if(teamId==null && teams.length()>0)teamId=teams.getJSONObject(0).getJSONObject("team").getInt("id");
        if(teamId==null)return;
        JSONObject fixtures=request("teamFixtures",params("team",String.valueOf(teamId),"next","10","timezone","America/Montevideo"));
        for(Match m:parseFavoriteFixtures(fixtures,selectedName))out.put(m.id,m);
    }

    private static List<Match> parseFavoriteFixtures(JSONObject wrapper,String selectedName)throws Exception{
        JSONArray a=apiResponse(wrapper);List<Match>result=new ArrayList<>();String base=selectedName.replace(" (Uruguay)","");
        for(int i=0;i<a.length();i++){
            JSONObject item=a.getJSONObject(i),fixture=item.getJSONObject("fixture"),league=item.getJSONObject("league"),teams=item.getJSONObject("teams");
            String home=teams.getJSONObject("home").getString("name"),away=teams.getJSONObject("away").getString("name");
            String opponent=home.equalsIgnoreCase(base)?away:home;long kickoff=fixture.getLong("timestamp")*1000L;
            if(kickoff>System.currentTimeMillis())result.add(new Match(fixture.getLong("id"),selectedName,opponent,league.optString("name","Partido"),kickoff,false));
        }
        result.sort((x,y)->Long.compare(x.kickoff,y.kickoff));return result;
    }

    private static boolean matchesTeam(String selected,String apiName,String country,boolean national,boolean apiNational){
        if(national)return apiNational&&(apiName.equalsIgnoreCase(selected)||country.equalsIgnoreCase(selected));
        String base=selected.replace(" (Uruguay)","");if(!apiName.equalsIgnoreCase(base))return false;
        String expected=AppStore.countryForClub(selected);return expected==null||country.equalsIgnoreCase(expected);
    }

    private static List<Match> parseFixtures(JSONObject wrapper,Set<String>clubCups,Set<String>nationalCups,boolean filter) throws Exception {
        JSONArray a=apiResponse(wrapper);List<Match> result=new ArrayList<>();Set<String>selected=new HashSet<>();
        if(clubCups!=null)for(String s:clubCups)selected.add(normalize(AppStore.shortName(s)));
        if(nationalCups!=null)for(String s:nationalCups)selected.add(normalize(AppStore.shortName(s)));
        for(int i=0;i<a.length();i++){
            JSONObject item=a.getJSONObject(i),fixture=item.getJSONObject("fixture"),league=item.getJSONObject("league"),teams=item.getJSONObject("teams");
            String competition=league.optString("name","Partido");if(filter&&!competitionSelected(competition,selected))continue;
            JSONObject home=teams.getJSONObject("home"),away=teams.getJSONObject("away");
            long kickoff=fixture.getLong("timestamp")*1000L;if(kickoff<System.currentTimeMillis()-7_200_000L)continue;
            result.add(new Match(fixture.getLong("id"),home.getString("name"),away.getString("name"),competition,kickoff,false));
        }
        result.sort((x,y)->Long.compare(x.kickoff,y.kickoff));return result;
    }

    private static boolean competitionSelected(String apiName,Set<String>selected){
        if(selected.isEmpty())return false;String n=normalize(apiName);
        for(String s:selected){if(n.contains(s)||s.contains(n))return true;
            if(s.equals("laliga")&&n.contains("laliga"))return true;
            if(s.contains("primera division")&&n.contains("primera division"))return true;
            if(s.contains("eliminatorias")&&(n.contains("world cup qualification")||n.contains("qualifiers")))return true;
        }return false;
    }
    private static String normalize(String s){return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}

    private static JSONArray apiResponse(JSONObject wrapper)throws Exception{
        if(!wrapper.optBoolean("ok"))throw new Exception(wrapper.optString("error","Error del intermediario"));
        JSONObject data=wrapper.getJSONObject("data");JSONObject errors=data.optJSONObject("errors");if(errors!=null&&errors.length()>0)throw new Exception(errors.toString());
        return data.getJSONArray("response");
    }

    private static JSONObject request(String action,Map<String,String> values)throws Exception{
        StringBuilder u=new StringBuilder(BuildConfig.PROXY_URL);u.append(BuildConfig.PROXY_URL.contains("?")?'&':'?');u.append("action=").append(enc(action));u.append("&token=").append(enc(BuildConfig.PROXY_TOKEN));
        for(Map.Entry<String,String>e:values.entrySet())u.append('&').append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        HttpURLConnection c=(HttpURLConnection)new URL(u.toString()).openConnection();c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);
        int status=c.getResponseCode();InputStream stream=status>=200&&status<400?c.getInputStream():c.getErrorStream();BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);r.close();c.disconnect();
        if(status<200||status>=400)throw new Exception("HTTP "+status);return new JSONObject(b.toString());
    }
    private static Map<String,String>params(String...v){Map<String,String>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put(v[i],v[i+1]);return m;}
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
}
