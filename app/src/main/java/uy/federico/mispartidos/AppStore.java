package uy.federico.mispartidos;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AppStore {
    static final long MATCH_DURATION_MS=135*60_000L;
    static final String[] CLUBS = {"Peñarol", "Nacional", "Defensor Sporting", "Liverpool (Uruguay)", "Danubio", "Cerro Largo",
            "River Plate", "Boca Juniors", "Racing Club", "Independiente", "San Lorenzo",
            "Flamengo", "Palmeiras", "Corinthians", "São Paulo", "Grêmio", "Internacional",
            "Barcelona", "Real Madrid", "Atlético de Madrid", "Sevilla", "Valencia", "Villarreal",
            "Manchester City", "Manchester United", "Chelsea", "Liverpool", "Arsenal", "Tottenham",
            "PSG", "Olympique de Marsella", "Monaco", "Bayern Múnich", "Borussia Dortmund",
            "Juventus", "Inter", "Milan", "Napoli", "Benfica", "Porto", "Ajax"};
    static final String[] NATIONAL_TEAMS = {
            "Uruguay", "Argentina", "Brasil", "Chile", "Colombia", "Paraguay", "Perú", "Ecuador", "Bolivia", "Venezuela",
            "Alemania", "Albania", "Austria", "Bélgica", "Bosnia y Herzegovina", "Bulgaria", "Croacia", "Dinamarca", "Escocia",
            "Eslovaquia", "Eslovenia", "España", "Finlandia", "Francia", "Gales", "Georgia", "Grecia", "Hungría", "Inglaterra",
            "Irlanda", "Irlanda del Norte", "Islandia", "Italia", "Noruega", "Países Bajos", "Polonia", "Portugal",
            "República Checa", "Rumania", "Serbia", "Suecia", "Suiza", "Turquía", "Ucrania",
            "México", "Estados Unidos", "Canadá", "Costa Rica", "Honduras", "Panamá", "Jamaica",
            "Argelia", "Camerún", "Costa de Marfil", "Egipto", "Ghana", "Marruecos", "Nigeria", "Senegal", "Sudáfrica", "Túnez",
            "Arabia Saudita", "Australia", "Catar", "China", "Corea del Sur", "Emiratos Árabes Unidos", "Irán", "Iraq", "Japón",
            "Nueva Zelanda"};
    static final String[] CLUB_COMPETITIONS = {
            "América del Sur › Uruguay › Primera División", "América del Sur › Uruguay › Copa AUF Uruguay", "América del Sur › Uruguay › Supercopa Uruguaya",
            "América del Sur › Argentina › Liga Profesional", "América del Sur › Argentina › Copa Argentina",
            "América del Sur › Brasil › Brasileirão", "América del Sur › Brasil › Copa do Brasil",
            "América del Sur › CONMEBOL › Copa Libertadores", "América del Sur › CONMEBOL › Copa Sudamericana",
            "Europa › España › LaLiga", "Europa › España › Copa del Rey", "Europa › España › Supercopa de España",
            "Europa › Inglaterra › Premier League", "Europa › Inglaterra › FA Cup", "Europa › Inglaterra › EFL Cup",
            "Europa › Francia › Ligue 1", "Europa › Francia › Coupe de France",
            "Europa › Alemania › Bundesliga", "Europa › Alemania › DFB-Pokal",
            "Europa › Italia › Serie A", "Europa › Italia › Coppa Italia",
            "Europa › Portugal › Primeira Liga", "Europa › Países Bajos › Eredivisie",
            "Europa › UEFA › Champions League", "Europa › UEFA › Europa League", "Europa › UEFA › Conference League",
            "Mundo › FIFA › Mundial de Clubes"};
    static final String[] NATIONAL_COMPETITIONS = {
            "América del Sur › CONMEBOL › Eliminatorias", "América del Sur › CONMEBOL › Copa América",
            "Europa › UEFA › Eurocopa", "Europa › UEFA › Nations League", "Europa › UEFA › Eliminatorias",
            "Mundo › FIFA › Copa del Mundo", "Mundo › FIFA › Amistosos internacionales"};
    private final SharedPreferences prefs;
    AppStore(Context context) { prefs=context.getSharedPreferences("mis_partidos",Context.MODE_PRIVATE); }

    Set<String> selectedTeams(){
        Set<String> saved=prefs.getStringSet("club_teams",null); if(saved!=null)return new LinkedHashSet<>(saved);
        Set<String> legacy=prefs.getStringSet("teams",null), result=new LinkedHashSet<>(); if(legacy!=null)for(String team:CLUBS)if(legacy.contains(team))result.add(team); return result;
    }
    void saveTeams(Set<String> teams){prefs.edit().putStringSet("club_teams",new HashSet<>(teams)).apply();}
    Set<String> selectedNationalTeams(){Set<String>s=prefs.getStringSet("national_teams",null);return s==null?new LinkedHashSet<>():new LinkedHashSet<>(s);}
    void saveNationalTeams(Set<String> teams){prefs.edit().putStringSet("national_teams",new HashSet<>(teams)).apply();}
    Set<String> selectedClubCompetitions(){Set<String>s=prefs.getStringSet("club_competitions",null);return s==null?suggestedClubCompetitions(selectedTeams()):new LinkedHashSet<>(s);}
    void saveClubCompetitions(Set<String> values){prefs.edit().putStringSet("club_competitions",new HashSet<>(values)).apply();}
    Set<String> selectedNationalCompetitions(){Set<String>s=prefs.getStringSet("national_competitions",null);return s==null?suggestedNationalCompetitions(selectedNationalTeams()):new LinkedHashSet<>(s);}
    void saveNationalCompetitions(Set<String> values){prefs.edit().putStringSet("national_competitions",new HashSet<>(values)).apply();}

    static Set<String> suggestedClubCompetitions(Set<String> teams){
        Set<String>r=new LinkedHashSet<>(); for(String team:teams){String country=countryForClub(team);if(country!=null)addContaining(r,CLUB_COMPETITIONS,country);}
        if(!teams.isEmpty())addContaining(r,CLUB_COMPETITIONS,"Champions League","Copa Libertadores","Copa Sudamericana","Mundial de Clubes");return r;
    }
    static Set<String> suggestedNationalCompetitions(Set<String> teams){
        Set<String>r=new LinkedHashSet<>();boolean southAmerica=false,europe=false;
        for(String team:teams){String continent=continentForNational(team);southAmerica|="América del Sur".equals(continent);europe|="Europa".equals(continent);}
        if(southAmerica)addContaining(r,NATIONAL_COMPETITIONS,"CONMEBOL");
        if(europe)addContaining(r,NATIONAL_COMPETITIONS,"UEFA");
        if(!teams.isEmpty())addContaining(r,NATIONAL_COMPETITIONS,"Mundo");
        return r;
    }
    private static boolean any(Set<String>v,String...w){for(String s:w)if(v.contains(s))return true;return false;}
    private static void addContaining(Set<String>o,String[]source,String...terms){for(String s:source)for(String t:terms)if(s.contains(t)){o.add(s);break;}}
    static String countryForClub(String t){if(any(new HashSet<>(java.util.Arrays.asList("Peñarol","Nacional","Defensor Sporting","Liverpool (Uruguay)","Danubio","Cerro Largo")),t))return"Uruguay";if(any(new HashSet<>(java.util.Arrays.asList("River Plate","Boca Juniors","Racing Club","Independiente","San Lorenzo")),t))return"Argentina";if(any(new HashSet<>(java.util.Arrays.asList("Flamengo","Palmeiras","Corinthians","São Paulo","Grêmio","Internacional")),t))return"Brasil";if(any(new HashSet<>(java.util.Arrays.asList("Barcelona","Real Madrid","Atlético de Madrid","Sevilla","Valencia","Villarreal")),t))return"España";if(any(new HashSet<>(java.util.Arrays.asList("Manchester City","Manchester United","Chelsea","Liverpool","Arsenal","Tottenham")),t))return"Inglaterra";if(any(new HashSet<>(java.util.Arrays.asList("PSG","Olympique de Marsella","Monaco")),t))return"Francia";if(t.contains("Bayern")||t.contains("Dortmund"))return"Alemania";if(any(new HashSet<>(java.util.Arrays.asList("Juventus","Inter","Milan","Napoli")),t))return"Italia";if(t.equals("Benfica")||t.equals("Porto"))return"Portugal";if(t.equals("Ajax"))return"Países Bajos";return null;}
    static String continentForClub(String t){String c=countryForClub(t);return c==null?null:(c.equals("Uruguay")||c.equals("Argentina")||c.equals("Brasil")?"América del Sur":"Europa");}
    static String continentForNational(String t){
        if(any(new HashSet<>(java.util.Arrays.asList("Uruguay","Argentina","Brasil","Chile","Colombia","Paraguay","Perú","Ecuador","Bolivia","Venezuela")),t))return"América del Sur";
        if(any(new HashSet<>(java.util.Arrays.asList("Alemania","Albania","Austria","Bélgica","Bosnia y Herzegovina","Bulgaria","Croacia","Dinamarca","Escocia","Eslovaquia","Eslovenia","España","Finlandia","Francia","Gales","Georgia","Grecia","Hungría","Inglaterra","Irlanda","Irlanda del Norte","Islandia","Italia","Noruega","Países Bajos","Polonia","Portugal","República Checa","Rumania","Serbia","Suecia","Suiza","Turquía","Ucrania")),t))return"Europa";
        if(any(new HashSet<>(java.util.Arrays.asList("México","Estados Unidos","Canadá","Costa Rica","Honduras","Panamá","Jamaica")),t))return"Norteamérica";
        if(any(new HashSet<>(java.util.Arrays.asList("Argelia","Camerún","Costa de Marfil","Egipto","Ghana","Marruecos","Nigeria","Senegal","Sudáfrica","Túnez")),t))return"África";
        if(any(new HashSet<>(java.util.Arrays.asList("Arabia Saudita","Catar","China","Corea del Sur","Emiratos Árabes Unidos","Irán","Iraq","Japón")),t))return"Asia";
        if(any(new HashSet<>(java.util.Arrays.asList("Australia","Nueva Zelanda")),t))return"Oceanía";return null;
    }
    static String shortName(String full){int p=full.lastIndexOf('›');return p<0?full:full.substring(p+1).trim();}

    int noticeMinutes(){return prefs.getInt("notice_minutes",60);} void saveNoticeMinutes(int m){prefs.edit().putInt("notice_minutes",m).apply();}
    int noticeMinutesFor(String team){try{return new JSONObject(prefs.getString("team_notice_minutes","{}")).optInt(team,noticeMinutes());}catch(Exception ignored){return noticeMinutes();}}
    void saveNoticeMinutesFor(String team,int minutes){try{JSONObject values=new JSONObject(prefs.getString("team_notice_minutes","{}"));if(minutes<0)values.remove(team);else values.put(team,minutes);prefs.edit().putString("team_notice_minutes",values.toString()).apply();}catch(Exception ignored){}}
    boolean immediateNoticeShown(Match m,int min){return prefs.getBoolean("shown_"+m.id+"_"+min,false);} void markImmediateNoticeShown(Match m,int min){prefs.edit().putBoolean("shown_"+m.id+"_"+min,true).apply();}
    List<Match>manualMatches(){List<Match>r=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("manual_matches","[]"));for(int i=0;i<a.length();i++)r.add(Match.fromJson(a.getJSONObject(i)));}catch(Exception ignored){}return r;}
    void addManual(Match m){List<Match>c=manualMatches();c.add(m);JSONArray a=new JSONArray();try{for(Match x:c)a.put(x.toJson());}catch(Exception ignored){}prefs.edit().putString("manual_matches",a.toString()).apply();}

    private List<Match>readMatches(String key){List<Match>r=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++)r.add(Match.fromJson(a.getJSONObject(i)));}catch(Exception ignored){}return r;}
    private String matchesJson(List<Match>matches){JSONArray a=new JSONArray();try{for(Match m:matches)a.put(m.toJson());}catch(Exception ignored){}return a.toString();}
    List<Match> apiFavoriteMatches(){return readMatches("api_favorite_matches");}
    List<Match> apiTodayMatches(){return readMatches("api_today_matches");}
    void saveApiMatches(List<Match>favorites,List<Match>today){prefs.edit().putString("api_favorite_matches",matchesJson(favorites)).putString("api_today_matches",matchesJson(today)).putLong("api_last_sync",System.currentTimeMillis()).apply();}
    boolean needsApiSync(){return System.currentTimeMillis()-prefs.getLong("api_last_sync",0)>43_200_000L;}
    long lastApiSync(){return prefs.getLong("api_last_sync",0);}
    long nextBackgroundSync(){return prefs.getLong("next_background_sync",0);}
    void saveNextBackgroundSync(long value){prefs.edit().putLong("next_background_sync",value).apply();}
    Set<String> scheduledAlarmIds(){Set<String>s=prefs.getStringSet("scheduled_alarm_ids",null);return s==null?new HashSet<>():new HashSet<>(s);}
    void saveScheduledAlarmIds(Set<String> ids){prefs.edit().putStringSet("scheduled_alarm_ids",new HashSet<>(ids)).apply();}
    String proxyUrl(){return prefs.getString("proxy_url","").trim();}
    String proxyToken(){return prefs.getString("proxy_token","").trim();}
    void saveProxy(String url,String token){prefs.edit().putString("proxy_url",url.trim()).putString("proxy_token",token.trim()).putLong("api_last_sync",0).apply();}
    String apiTeamId(String name){try{JSONObject ids=new JSONObject(prefs.getString("goal_team_ids","{}"));return ids.has(name)?ids.getString(name):null;}catch(Exception ignored){return null;}}
    void saveApiTeamId(String name,String id){try{JSONObject ids=new JSONObject(prefs.getString("goal_team_ids","{}"));ids.put(name,id);prefs.edit().putString("goal_team_ids",ids.toString()).apply();}catch(Exception ignored){}}
    void clearApiTeamId(String name){try{JSONObject ids=new JSONObject(prefs.getString("goal_team_ids","{}"));ids.remove(name);prefs.edit().putString("goal_team_ids",ids.toString()).apply();}catch(Exception ignored){}}
    String apiTeamCountry(String name){try{return new JSONObject(prefs.getString("goal_team_countries","{}")).optString(name,null);}catch(Exception ignored){return null;}}
    void saveApiTeamCountry(String name,String country){if(country==null||country.isEmpty())return;try{JSONObject values=new JSONObject(prefs.getString("goal_team_countries","{}"));values.put(name,country);prefs.edit().putString("goal_team_countries",values.toString()).apply();}catch(Exception ignored){}}
    void saveDynamicTeamCompetition(String team,String competition){if(team==null||competition==null||competition.isEmpty())return;try{JSONObject map=new JSONObject(prefs.getString("dynamic_team_competitions","{}"));JSONArray values=map.optJSONArray(team);if(values==null)values=new JSONArray();boolean found=false;for(int i=0;i<values.length();i++)if(competition.equals(values.optString(i)))found=true;if(!found)values.put(competition);map.put(team,values);Set<String>all=new LinkedHashSet<>(prefs.getStringSet("dynamic_competitions",new HashSet<>()));all.add(competition);prefs.edit().putString("dynamic_team_competitions",map.toString()).putStringSet("dynamic_competitions",new HashSet<>(all)).apply();}catch(Exception ignored){}}
    void saveDynamicCompetition(String competition){if(competition==null||competition.isEmpty())return;Set<String>all=new LinkedHashSet<>(prefs.getStringSet("dynamic_competitions",new HashSet<>()));all.add(competition);prefs.edit().putStringSet("dynamic_competitions",new HashSet<>(all)).apply();}
    Set<String> dynamicSuggestedClubCompetitions(Set<String>teams){Set<String>result=new LinkedHashSet<>();try{JSONObject map=new JSONObject(prefs.getString("dynamic_team_competitions","{}"));for(String team:teams){JSONArray values=map.optJSONArray(team);if(values!=null)for(int i=0;i<values.length();i++)result.add(values.optString(i));}}catch(Exception ignored){}return result;}
    String[] allClubCompetitions(){Set<String>all=new LinkedHashSet<>(java.util.Arrays.asList(CLUB_COMPETITIONS));all.addAll(prefs.getStringSet("dynamic_competitions",new HashSet<>()));return all.toArray(new String[0]);}

    List<Match>upcoming(){
        long now=System.currentTimeMillis();Set<String>favorites=new HashSet<>(selectedTeams());favorites.addAll(selectedNationalTeams());List<Match>r=new ArrayList<>();for(Match m:manualMatches())if(m.kickoff+MATCH_DURATION_MS>now)r.add(m);
        java.util.LinkedHashMap<String,Match>unique=new java.util.LinkedHashMap<>();
        for(Match m:readMatches("api_favorite_matches"))if(m.kickoff+MATCH_DURATION_MS>now&&favorites.contains(m.team)){
            String a=m.team.toLowerCase(java.util.Locale.ROOT),b=m.opponent.toLowerCase(java.util.Locale.ROOT);String pair=a.compareTo(b)<=0?a+"|"+b:b+"|"+a;String key=(m.kickoff/60_000L)+"|"+pair;
            Match old=unique.get(key);if(old==null)unique.put(key,m);else unique.put(key,new Match(Math.min(old.id,m.id),old.team+" vs. "+old.opponent,"",old.competition,old.kickoff,false));
        }
        r.addAll(unique.values());
        Collections.sort(r,(a,b)->Long.compare(a.kickoff,b.kickoff));return r;
    }
    List<Match>todayByCompetitions(){
        long now=System.currentTimeMillis();List<Match>result=new ArrayList<>();for(Match m:apiTodayMatches())if(m.kickoff+MATCH_DURATION_MS>now)result.add(m);return result;
    }
    static boolean isInProgress(Match match){long now=System.currentTimeMillis();return match.kickoff<=now&&match.kickoff+MATCH_DURATION_MS>now;}
    private String[]sampleTeamsFor(String c){
        if(c.contains("Uruguay"))return new String[]{"Defensor Sporting","Danubio"};
        if(c.contains("Argentina"))return new String[]{"Boca Juniors","Racing Club"};
        if(c.contains("Brasil"))return new String[]{"Flamengo","Palmeiras"};
        if(c.contains("España"))return new String[]{"Sevilla","Valencia"};
        if(c.contains("Inglaterra"))return new String[]{"Arsenal","Tottenham"};
        if(c.contains("Francia"))return new String[]{"PSG","Monaco"};
        if(c.contains("CONMEBOL"))return new String[]{"River Plate","Flamengo"};
        if(c.contains("UEFA"))return new String[]{"Bayern Múnich","Inter"};
        if(c.contains("selecciones")||c.contains("Copa América")||c.contains("Eliminatorias"))return new String[]{"Uruguay","Argentina"};
        return new String[]{"Barcelona","Manchester City"};
    }
    private String clubCompetition(String team,Set<String>selected){Set<String>c=suggestedClubCompetitions(new HashSet<>(Collections.singletonList(team)));for(String x:CLUB_COMPETITIONS)if(selected.contains(x)&&c.contains(x))return x;return null;}
    private String nationalCompetition(String team,Set<String>selected){Set<String>c=suggestedNationalCompetitions(new HashSet<>(Collections.singletonList(team)));for(String x:NATIONAL_COMPETITIONS)if(selected.contains(x)&&c.contains(x))return x;return null;}
    private String rival(String t){if(t.equals("Peñarol"))return"Nacional";if(t.equals("Barcelona"))return"Sevilla";if(t.equals("Real Madrid"))return"Valencia";if(t.equals("Atlético de Madrid"))return"Villarreal";if(t.equals("Manchester City"))return"Arsenal";if(t.equals("Manchester United"))return"Tottenham";if(t.equals("Chelsea"))return"Newcastle";if(t.equals("Liverpool"))return"Everton";return"Olympique de Marsella";}
    private String nationalRival(String t){if(t.equals("Uruguay"))return"Argentina";if(t.equals("Argentina"))return"Brasil";if(t.equals("Brasil"))return"Uruguay";if(t.equals("España"))return"Italia";if(t.equals("Francia"))return"Alemania";return"Países Bajos";}
}
