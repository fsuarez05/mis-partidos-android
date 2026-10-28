package uy.federico.mispartidos;

import org.json.JSONObject;
import java.util.Collection;

/** Small deterministic rules shared by the foreground refresher and its tests. */
final class LiveRefreshPolicy {
    static final long INTERVAL_MS = 5L * 60L * 1000L;

    private LiveRefreshPolicy() {}

    static boolean shouldStart(long now, long lastStartedAt, boolean foreground,
                               boolean hasLiveMatches, boolean inFlight, boolean configured) {
        return foreground && hasLiveMatches && !inFlight && configured
                && (lastStartedAt <= 0L || now - lastStartedAt >= INTERVAL_MS);
    }

    static boolean containsLive(Collection<Match> matches, long now) {
        if (matches == null) return false;
        for (Match match : matches) {
            if (isLiveCandidate(match.manual,match.status,match.kickoff,now)) return true;
        }
        return false;
    }

    static boolean isLiveCandidate(boolean manual,String status,long kickoff,long now){
        return !manual&&FixtureStatusPolicy.isInProgress(status,kickoff,now,AppStore.MATCH_DURATION_MS);
    }

    static boolean isUsableFixtureResponse(JSONObject response){
        if(response==null||!response.optBoolean("ok"))return false;
        JSONObject payload=response.optJSONObject("data");
        return payload!=null&&payload.optBoolean("success",true)&&payload.optJSONArray("data")!=null;
    }

    /** A score is usable only when GOAL supplies both sides. */
    static int[] completeScore(JSONObject fixture) {
        if (fixture == null) return null;
        int home = scoreValue(fixture, "homeTeamFtScore", "homeTeamScore", "homeScore");
        int away = scoreValue(fixture, "awayTeamFtScore", "awayTeamScore", "awayScore");
        JSONObject score = fixture.optJSONObject("score");
        if (score != null) {
            if (home < 0) home = integer(score, "home");
            if (away < 0) away = integer(score, "away");
            JSONObject fullTime = score.optJSONObject("fullTime");
            if (fullTime != null) {
                if (home < 0) home = integer(fullTime, "home");
                if (away < 0) away = integer(fullTime, "away");
            }
        }
        return home < 0 || away < 0 ? null : new int[]{home, away};
    }

    private static int scoreValue(JSONObject fixture, String first, String second, String third) {
        int value = integer(fixture, first);
        if (value < 0) value = integer(fixture, second);
        if (value < 0) value = integer(fixture, third);
        return value;
    }

    private static int integer(JSONObject value, String key) {
        if (value == null || !value.has(key) || value.isNull(key)) return -1;
        Object raw = value.opt(key);
        if (raw instanceof Number) return ((Number) raw).intValue();
        try { return Integer.parseInt(String.valueOf(raw)); }
        catch (Exception ignored) { return -1; }
    }
}
