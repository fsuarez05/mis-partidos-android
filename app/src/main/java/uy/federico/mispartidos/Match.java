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

    public Match(long id, String team, String opponent, String competition, long kickoff, boolean manual) {
        this.id = id; this.team = team; this.opponent = opponent;
        this.competition = competition; this.kickoff = kickoff; this.manual = manual;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("team", team).put("opponent", opponent)
                .put("competition", competition).put("kickoff", kickoff).put("manual", manual);
    }

    static Match fromJson(JSONObject j) throws JSONException {
        return new Match(j.getLong("id"), j.getString("team"), j.getString("opponent"),
                j.optString("competition", "Partido"), j.getLong("kickoff"), j.optBoolean("manual"));
    }
}
