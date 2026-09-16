package uy.federico.mispartidos;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AppStore {
    static final String[] CLUBS = {"Peñarol", "Nacional", "Defensor Sporting", "Liverpool (Uruguay)", "Danubio", "Cerro Largo",
            "River Plate", "Boca Juniors", "Racing Club", "Independiente", "San Lorenzo",
            "Flamengo", "Palmeiras", "Corinthians", "São Paulo", "Grêmio", "Internacional",
            "Barcelona", "Real Madrid", "Atlético de Madrid", "Sevilla", "Valencia", "Villarreal",
            "Manchester City", "Manchester United", "Chelsea", "Liverpool", "Arsenal", "Tottenham",
            "PSG", "Olympique de Marsella", "Monaco", "Bayern Múnich", "Borussia Dortmund",
            "Juventus", "Inter", "Milan", "Napoli", "Benfica", "Porto", "Ajax"};
    static final String[] NATIONAL_TEAMS = {"Uruguay", "Argentina", "Brasil", "Chile", "Colombia", "Paraguay", "Perú", "Ecuador", "Bolivia", "Venezuela",
            "España", "Francia", "Inglaterra", "Alemania", "Italia", "Portugal", "Países Bajos", "Bélgica", "Croacia",
            "México", "Estados Unidos", "Canadá", "Japón", "Corea del Sur", "Australia", "Marruecos", "Senegal", "Nigeria"};
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
    static Set<String> suggestedNationalCompetitions(Set<String> teams){Set<String>r=new LinkedHashSet<>();if(any(teams,"Uruguay","Argentina","Brasil"))addContaining(r,NATIONAL_COMPETITIONS,"CONMEBOL","Mundo");if(any(teams,"España","Francia","Inglaterra"))addContaining(r,NATIONAL_COMPETITIONS,"UEFA","Mundo");return r;}
    private static boolean any(Set<String>v,String...w){for(String s:w)if(v.contains(s))return true;return false;}
    private static void addContaining(Set<String>o,String[]source,String...terms){for(String s:source)for(String t:terms)if(s.contains(t)){o.add(s);break;}}
    static String countryForClub(String t){if(any(new HashSet<>(java.util.Arrays.asList("Peñarol","Nacional","Defensor Sporting","Liverpool (Uruguay)","Danubio","Cerro Largo")),t))return"Uruguay";if(any(new HashSet<>(java.util.Arrays.asList("River Plate","Boca Juniors","Racing Club","Independiente","San Lorenzo")),t))return"Argentina";if(any(new HashSet<>(java.util.Arrays.asList("Flamengo","Palmeiras","Corinthians","São Paulo","Grêmio","Internacional")),t))return"Brasil";if(any(new HashSet<>(java.util.Arrays.asList("Barcelona","Real Madrid","Atlético de Madrid","Sevilla","Valencia","Villarreal")),t))return"España";if(any(new HashSet<>(java.util.Arrays.asList("Manchester City","Manchester United","Chelsea","Liverpool","Arsenal","Tottenham")),t))return"Inglaterra";if(any(new HashSet<>(java.util.Arrays.asList("PSG","Olympique de Marsella","Monaco")),t))return"Francia";if(t.contains("Bayern")||t.contains("Dortmund"))return"Alemania";if(any(new HashSet<>(java.util.Arrays.asList("Juventus","Inter","Milan","Napoli")),t))return"Italia";if(t.equals("Benfica")||t.equals("Porto"))return"Portugal";if(t.equals("Ajax"))return"Países Bajos";return null;}
    static String continentForClub(String t){String c=countryForClub(t);return c==null?null:(c.equals("Uruguay")||c.equals("Argentina")||c.equals("Brasil")?"América del Sur":"Europa");}
    static String continentForNational(String t){
        if(any(new HashSet<>(java.util.Arrays.asList("Uruguay","Argentina","Brasil","Chile","Colombia","Paraguay","Perú","Ecuador","Bolivia","Venezuela")),t))return"América del Sur";
        if(any(new HashSet<>(java.util.Arrays.asList("España","Francia","Inglaterra","Alemania","Italia","Portugal","Países Bajos","Bélgica","Croacia")),t))return"Europa";
        if(any(new HashSet<>(java.util.Arrays.asList("México","Estados Unidos","Canadá")),t))return"Norteamérica";
        if(any(new HashSet<>(java.util.Arrays.asList("Marruecos","Senegal","Nigeria")),t))return"África";
        if(any(new HashSet<>(java.util.Arrays.asList("Japón","Corea del Sur")),t))return"Asia";
        if(t.equals("Australia"))return"Oceanía";return null;
    }
    static String shortName(String full){int p=full.lastIndexOf('›');return p<0?full:full.substring(p+1).trim();}

    int noticeMinutes(){return prefs.getInt("notice_minutes",60);} void saveNoticeMinutes(int m){prefs.edit().putInt("notice_minutes",m).apply();}
    boolean immediateNoticeShown(Match m,int min){return prefs.getBoolean("shown_"+m.id+"_"+min,false);} void markImmediateNoticeShown(Match m,int min){prefs.edit().putBoolean("shown_"+m.id+"_"+min,true).apply();}
    List<Match>manualMatches(){List<Match>r=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("manual_matches","[]"));for(int i=0;i<a.length();i++)r.add(Match.fromJson(a.getJSONObject(i)));}catch(Exception ignored){}return r;}
    void addManual(Match m){List<Match>c=manualMatches();c.add(m);JSONArray a=new JSONArray();try{for(Match x:c)a.put(x.toJson());}catch(Exception ignored){}prefs.edit().putString("manual_matches",a.toString()).apply();}

    private List<Match>readMatches(String key){List<Match>r=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++)r.add(Match.fromJson(a.getJSONObject(i)));}catch(Exception ignored){}return r;}
    private String matchesJson(List<Match>matches){JSONArray a=new JSONArray();try{for(Match m:matches)a.put(m.toJson());}catch(Exception ignored){}return a.toString();}
    void saveApiMatches(List<Match>favorites,List<Match>today){prefs.edit().putString("api_favorite_matches",matchesJson(favorites)).putString("api_today_matches",matchesJson(today)).putLong("api_last_sync",System.currentTimeMillis()).apply();}
    boolean needsApiSync(){return System.currentTimeMillis()-prefs.getLong("api_last_sync",0)>21_600_000L;}
    long lastApiSync(){return prefs.getLong("api_last_sync",0);}
    String proxyUrl(){return prefs.getString("proxy_url","").trim();}
    String proxyToken(){return prefs.getString("proxy_token","").trim();}
    void saveProxy(String url,String token){prefs.edit().putString("proxy_url",url.trim()).putString("proxy_token",token.trim()).putLong("api_last_sync",0).apply();}

    List<Match>upcoming(){
        long now=System.currentTimeMillis();List<Match>r=new ArrayList<>();for(Match m:manualMatches())if(m.kickoff>now)r.add(m);for(Match m:readMatches("api_favorite_matches"))if(m.kickoff>now)r.add(m);
        Collections.sort(r,(a,b)->Long.compare(a.kickoff,b.kickoff));return r;
    }
    List<Match>todayByCompetitions(){
        return readMatches("api_today_matches");
    }
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
