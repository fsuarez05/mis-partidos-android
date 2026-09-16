package uy.federico.mispartidos;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppStore {
    static final String[] ALL_TEAMS = {"Peñarol", "Selección uruguaya", "Barcelona", "Real Madrid",
            "Atlético de Madrid", "Manchester City", "Manchester United", "Chelsea", "Liverpool", "PSG"};
    private final SharedPreferences prefs;

    AppStore(Context context) { prefs = context.getSharedPreferences("mis_partidos", Context.MODE_PRIVATE); }

    Set<String> selectedTeams() {
        Set<String> saved = prefs.getStringSet("teams", null);
        return saved == null ? new HashSet<>(Arrays.asList(ALL_TEAMS)) : new HashSet<>(saved);
    }

    void saveTeams(Set<String> teams) { prefs.edit().putStringSet("teams", new HashSet<>(teams)).apply(); }
    int noticeMinutes() { return prefs.getInt("notice_minutes", 60); }
    void saveNoticeMinutes(int minutes) { prefs.edit().putInt("notice_minutes", minutes).apply(); }

    List<Match> manualMatches() {
        List<Match> result = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(prefs.getString("manual_matches", "[]"));
            for (int i = 0; i < items.length(); i++) result.add(Match.fromJson(items.getJSONObject(i)));
        } catch (Exception ignored) { }
        return result;
    }

    void addManual(Match match) {
        List<Match> current = manualMatches(); current.add(match);
        JSONArray json = new JSONArray();
        try { for (Match m : current) json.put(m.toJson()); } catch (Exception ignored) { }
        prefs.edit().putString("manual_matches", json.toString()).apply();
    }

    List<Match> upcoming() {
        long now = System.currentTimeMillis();
        List<Match> result = new ArrayList<>();
        for (Match m : manualMatches()) if (m.kickoff > now) result.add(m);
        Set<String> teams = selectedTeams();
        long day = 24L * 60 * 60 * 1000;
        for (int i = 0; i < ALL_TEAMS.length; i++) {
            if (!teams.contains(ALL_TEAMS[i])) continue;
            long kickoff = now + ((i / 2) + 1) * day + (i % 2) * 3L * 60 * 60 * 1000;
            result.add(new Match(10_000 + i, ALL_TEAMS[i], rival(ALL_TEAMS[i]), competition(ALL_TEAMS[i]), kickoff, false));
        }
        Collections.sort(result, (a, b) -> Long.compare(a.kickoff, b.kickoff));
        return result;
    }

    private String rival(String team) {
        if (team.equals("Peñarol")) return "Nacional";
        if (team.equals("Selección uruguaya")) return "Argentina";
        if (team.equals("Barcelona")) return "Sevilla";
        if (team.equals("Real Madrid")) return "Valencia";
        if (team.equals("Atlético de Madrid")) return "Villarreal";
        if (team.equals("Manchester City")) return "Arsenal";
        if (team.equals("Manchester United")) return "Tottenham";
        if (team.equals("Chelsea")) return "Newcastle";
        if (team.equals("Liverpool")) return "Everton";
        return "Olympique de Marsella";
    }

    private String competition(String team) {
        if (team.equals("Peñarol")) return "Campeonato Uruguayo · dato de prueba";
        if (team.equals("Selección uruguaya")) return "Eliminatorias · dato de prueba";
        if (team.equals("PSG")) return "Ligue 1 · dato de prueba";
        if (team.contains("Manchester") || team.equals("Chelsea") || team.equals("Liverpool")) return "Premier League · dato de prueba";
        return "LaLiga · dato de prueba";
    }
}
