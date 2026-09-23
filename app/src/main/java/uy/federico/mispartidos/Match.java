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
    public final String fixtureId;
    public final String homeTeam;
    public final String awayTeam;
    public final String homeTeamId;
    public final String awayTeamId;
    public final String country;

    public Match(long id, String team, String opponent, String competition, long kickoff, boolean manual) {
        this(id,team,opponent,competition,kickoff,manual,-1,-1);
    }

    public Match(long id, String team, String opponent, String competition, long kickoff, boolean manual, int homeScore, int awayScore) {
        this(id,team,opponent,competition,kickoff,manual,homeScore,awayScore,
                "",team,opponent,"","","");
    }

    public Match(long id, String team, String opponent, String competition, long kickoff, boolean manual,
                 int homeScore, int awayScore, String fixtureId, String homeTeam, String awayTeam,
                 String homeTeamId, String awayTeamId, String country) {
        this.id = id; this.team = team; this.opponent = opponent;
        this.competition = competition; this.kickoff = kickoff; this.manual = manual;
        this.homeScore=homeScore;this.awayScore=awayScore;
        this.fixtureId=clean(fixtureId);this.homeTeam=clean(homeTeam);this.awayTeam=clean(awayTeam);
        this.homeTeamId=clean(homeTeamId);this.awayTeamId=clean(awayTeamId);this.country=clean(country);
    }

    public boolean hasScore(){return homeScore>=0&&awayScore>=0;}

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("id", id).put("team", team).put("opponent", opponent)
                .put("competition", competition).put("kickoff", kickoff).put("manual", manual)
                .put("homeScore",homeScore).put("awayScore",awayScore)
                .put("fixtureId",fixtureId).put("homeTeam",homeTeam).put("awayTeam",awayTeam)
                .put("homeTeamId",homeTeamId).put("awayTeamId",awayTeamId).put("country",country);
    }

    static Match fromJson(JSONObject j) throws JSONException {
        boolean manual=j.optBoolean("manual");String team=j.getString("team"),opponent=j.getString("opponent");
        return new Match(j.getLong("id"),team,opponent,j.optString("competition", "Partido"),
                j.getLong("kickoff"),manual,j.optInt("homeScore",-1),j.optInt("awayScore",-1),
                j.optString("fixtureId",""),
                j.optString("homeTeam",team),j.optString("awayTeam",opponent),
                j.optString("homeTeamId"),j.optString("awayTeamId"),j.optString("country"));
    }

    String local(){return homeTeam.isEmpty()?team:homeTeam;}
    String visitante(){return awayTeam.isEmpty()?opponent:awayTeam;}
    boolean hasRealFixtureId(){return !manual&&!fixtureId.isEmpty();}
    private static String clean(String value){return value==null?"":value.trim();}
}
