package uy.federico.mispartidos;

import java.text.Normalizer;
import java.util.Locale;

/** Deterministic interpretation of GOAL status labels and kickoff timestamps. */
final class FixtureStatusPolicy {
    static final String SCHEDULED = "SCHEDULED";
    static final String LIVE = "LIVE";
    static final String FINISHED = "FINISHED";
    static final String POSTPONED = "POSTPONED";

    private FixtureStatusPolicy() {}

    static String classify(String rawStatus, long kickoff, long now, long normalDurationMs) {
        String status = normalize(rawStatus);
        if (status.contains("postponed") || status.contains("cancelled") || status.contains("canceled") || status.contains("abandoned")) return POSTPONED;
        // A future kickoff is authoritative if GOAL returns an inconsistent terminal status.
        if (kickoff > now) return SCHEDULED;
        if (status.contains("finished") || status.contains("full time") || status.equals("ft")
                || status.contains("after penalties") || status.contains("after extra time")
                || status.contains("ended") || status.equals("final")) return FINISHED;
        if (status.contains("live") || status.contains("playing") || status.contains("in progress")
                || status.contains("half time") || status.contains("halftime")
                || status.contains("1st half") || status.contains("2nd half") || status.equals("ht")) return LIVE;
        return now >= kickoff + normalDurationMs ? FINISHED : LIVE;
    }

    static boolean isFinished(String rawStatus, long kickoff, long now, long durationMs) {
        return FINISHED.equals(classify(rawStatus, kickoff, now, durationMs));
    }

    static boolean isInProgress(String state, long kickoff, long now, long durationMs) {
        if (LIVE.equals(state)) return true;
        if (FINISHED.equals(state) || POSTPONED.equals(state)) return false;
        // A saved scheduled status can be stale by kickoff time; use the match
        // window as a fallback until a newer provider status arrives.
        return kickoff <= now && now < kickoff + durationMs;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
