package uy.federico.mispartidos;

import org.json.JSONException;
import org.json.JSONObject;

public class Match {
    public final long id;
    public final String team;
    public final String opponent;
    public final String competition;
    public final long kickoff;
    public final boolean manual;
    public final int homeScore;
    public final int awayScore;

    public Match(long id, String team, String opponent, String competition, long kickoff, boolean manual) {
        this(id,team,opponent,competition,kickoff,manual,-1,-1);
    }

    public Match(long id, String team, String opponent, String competition, long kickoff, boolean manual, int homeScore, int awayScore) {
        this.id = id; this.team = team; this.opponent = opponent;
        this.competition = competition; this.kickoff = kickoff; this.manual = manual;
        this.homeScore=homeScore;this.awayScore=awayScore;
    }

    public boolean hasScore(){return homeScore>=0&&awayScore>=0;}

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("team", team).put("opponent", opponent)
                .put("competition", competition).put("kickoff", kickoff).put("manual", manual)
                .put("homeScore",homeScore).put("awayScore",awayScore);
    }

    static Match fromJson(JSONObject j) throws JSONException {
        return new Match(j.getLong("id"), j.getString("team"), j.getString("opponent"),
                j.optString("competition", "Partido"), j.getLong("kickoff"), j.optBoolean("manual"),j.optInt("homeScore",-1),j.optInt("awayScore",-1));
    }
}
