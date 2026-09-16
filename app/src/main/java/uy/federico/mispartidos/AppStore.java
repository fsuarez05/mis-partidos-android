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
    static final String[] CLUBS = {"Peñarol", "Barcelona", "Real Madrid", "Atlético de Madrid", "Manchester City", "Manchester United", "Chelsea", "Liverpool", "PSG"};
    static final String[] NATIONAL_TEAMS = {"Uruguay", "Argentina", "Brasil", "España", "Francia", "Inglaterra"};
    static final String[] CLUB_COMPETITIONS = {
            "América del Sur › Uruguay › Primera División", "América del Sur › Uruguay › Copa AUF Uruguay", "América del Sur › Uruguay › Supercopa Uruguaya",
            "América del Sur › CONMEBOL › Copa Libertadores", "América del Sur › CONMEBOL › Copa Sudamericana",
            "Europa › España › LaLiga", "Europa › España › Copa del Rey", "Europa › España › Supercopa de España",
            "Europa › Inglaterra › Premier League", "Europa › Inglaterra › FA Cup", "Europa › Inglaterra › EFL Cup",
            "Europa › Francia › Ligue 1", "Europa › Francia › Coupe de France",
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
        Set<String> legacy=prefs.getStringSet("teams",null), result=new LinkedHashSet<>(); for(String team:CLUBS)if(legacy==null||legacy.contains(team))result.add(team); return result;
    }
    void saveTeams(Set<String> teams){prefs.edit().putStringSet("club_teams",new HashSet<>(teams)).apply();}
    Set<String> selectedNationalTeams(){Set<String>s=prefs.getStringSet("national_teams",null);return s==null?new LinkedHashSet<>(Collections.singletonList("Uruguay")):new LinkedHashSet<>(s);}
    void saveNationalTeams(Set<String> teams){prefs.edit().putStringSet("national_teams",new HashSet<>(teams)).apply();}
    Set<String> selectedClubCompetitions(){Set<String>s=prefs.getStringSet("club_competitions",null);return s==null?suggestedClubCompetitions(selectedTeams()):new LinkedHashSet<>(s);}
    void saveClubCompetitions(Set<String> values){prefs.edit().putStringSet("club_competitions",new HashSet<>(values)).apply();}
    Set<String> selectedNationalCompetitions(){Set<String>s=prefs.getStringSet("national_competitions",null);return s==null?suggestedNationalCompetitions(selectedNationalTeams()):new LinkedHashSet<>(s);}
    void saveNationalCompetitions(Set<String> values){prefs.edit().putStringSet("national_competitions",new HashSet<>(values)).apply();}

    static Set<String> suggestedClubCompetitions(Set<String> teams){
        Set<String>r=new LinkedHashSet<>(); if(teams.contains("Peñarol"))addContaining(r,CLUB_COMPETITIONS,"Uruguay","CONMEBOL");
        if(any(teams,"Barcelona","Real Madrid","Atlético de Madrid"))addContaining(r,CLUB_COMPETITIONS,"España","Champions");
        if(any(teams,"Manchester City","Manchester United","Chelsea","Liverpool"))addContaining(r,CLUB_COMPETITIONS,"Inglaterra","Champions");
        if(teams.contains("PSG"))addContaining(r,CLUB_COMPETITIONS,"Francia","Champions"); return r;
    }
    static Set<String> suggestedNationalCompetitions(Set<String> teams){Set<String>r=new LinkedHashSet<>();if(any(teams,"Uruguay","Argentina","Brasil"))addContaining(r,NATIONAL_COMPETITIONS,"CONMEBOL","Mundo");if(any(teams,"España","Francia","Inglaterra"))addContaining(r,NATIONAL_COMPETITIONS,"UEFA","Mundo");return r;}
    private static boolean any(Set<String>v,String...w){for(String s:w)if(v.contains(s))return true;return false;}
    private static void addContaining(Set<String>o,String[]source,String...terms){for(String s:source)for(String t:terms)if(s.contains(t)){o.add(s);break;}}
    static String shortName(String full){int p=full.lastIndexOf('›');return p<0?full:full.substring(p+1).trim();}

    int noticeMinutes(){return prefs.getInt("notice_minutes",60);} void saveNoticeMinutes(int m){prefs.edit().putInt("notice_minutes",m).apply();}
    boolean immediateNoticeShown(Match m,int min){return prefs.getBoolean("shown_"+m.id+"_"+min,false);} void markImmediateNoticeShown(Match m,int min){prefs.edit().putBoolean("shown_"+m.id+"_"+min,true).apply();}
    List<Match>manualMatches(){List<Match>r=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("manual_matches","[]"));for(int i=0;i<a.length();i++)r.add(Match.fromJson(a.getJSONObject(i)));}catch(Exception ignored){}return r;}
    void addManual(Match m){List<Match>c=manualMatches();c.add(m);JSONArray a=new JSONArray();try{for(Match x:c)a.put(x.toJson());}catch(Exception ignored){}prefs.edit().putString("manual_matches",a.toString()).apply();}

    List<Match>upcoming(){
        long now=System.currentTimeMillis(),day=86_400_000L;List<Match>r=new ArrayList<>();for(Match m:manualMatches())if(m.kickoff>now)r.add(m);int i=0;
        Set<String>clubs=selectedTeams(),clubCups=selectedClubCompetitions();for(String team:CLUBS)if(clubs.contains(team)){String cup=clubCompetition(team,clubCups);if(cup!=null)r.add(new Match(10_000+i,team,rival(team),shortName(cup)+" · dato de prueba",now+(i/2+1)*day+(i%2)*10_800_000L,false));i++;}
        Set<String>countries=selectedNationalTeams(),nationalCups=selectedNationalCompetitions();for(String team:NATIONAL_TEAMS)if(countries.contains(team)){String cup=nationalCompetition(team,nationalCups);if(cup!=null)r.add(new Match(20_000+i,team,nationalRival(team),shortName(cup)+" · dato de prueba",now+(i/2+2)*day,false));i++;}
        Collections.sort(r,(a,b)->Long.compare(a.kickoff,b.kickoff));return r;
    }
    private String clubCompetition(String team,Set<String>selected){Set<String>c=suggestedClubCompetitions(new HashSet<>(Collections.singletonList(team)));for(String x:CLUB_COMPETITIONS)if(selected.contains(x)&&c.contains(x))return x;return null;}
    private String nationalCompetition(String team,Set<String>selected){Set<String>c=suggestedNationalCompetitions(new HashSet<>(Collections.singletonList(team)));for(String x:NATIONAL_COMPETITIONS)if(selected.contains(x)&&c.contains(x))return x;return null;}
    private String rival(String t){if(t.equals("Peñarol"))return"Nacional";if(t.equals("Barcelona"))return"Sevilla";if(t.equals("Real Madrid"))return"Valencia";if(t.equals("Atlético de Madrid"))return"Villarreal";if(t.equals("Manchester City"))return"Arsenal";if(t.equals("Manchester United"))return"Tottenham";if(t.equals("Chelsea"))return"Newcastle";if(t.equals("Liverpool"))return"Everton";return"Olympique de Marsella";}
    private String nationalRival(String t){if(t.equals("Uruguay"))return"Argentina";if(t.equals("Argentina"))return"Brasil";if(t.equals("Brasil"))return"Uruguay";if(t.equals("España"))return"Italia";if(t.equals("Francia"))return"Alemania";return"Países Bajos";}
}
