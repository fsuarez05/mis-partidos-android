package uy.federico.mispartidos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merge rules for incomplete fixture responses and previously stored scores. */
final class MatchDataPolicy {
    private MatchDataPolicy() {}

    /** The newer fixture's complete score wins; an absent score never erases one. */
    static Match merge(Match latest, Match previous) {
        if (latest == null) return previous;
        if (previous == null) return latest;
        int home = latest.hasScore() ? latest.homeScore : previous.hasScore() ? previous.homeScore : -1;
        int away = latest.hasScore() ? latest.awayScore : previous.hasScore() ? previous.awayScore : -1;
        String status = latest.status == null || latest.status.trim().isEmpty()
                || "UNKNOWN".equalsIgnoreCase(latest.status) ? previous.status : latest.status;
        if (FixtureStatusPolicy.FINISHED.equals(previous.status)
                && (FixtureStatusPolicy.SCHEDULED.equals(status) || FixtureStatusPolicy.LIVE.equals(status))) {
            status = FixtureStatusPolicy.FINISHED;
        }
        return new Match(latest.id, latest.team, latest.opponent, latest.competition, latest.kickoff,
                latest.manual, home, away, latest.fixtureId, latest.homeTeam, latest.awayTeam,
                latest.homeTeamId, latest.awayTeamId, latest.country, status);
    }

    /** Keeps the order of the latest list and enriches its rows from saved data. */
    static List<Match> enrich(List<Match> latest, List<Match> previous) {
        Map<String, Match> fallback = byFixture(previous);
        List<Match> result = new ArrayList<>();
        if (latest == null) return result;
        for (Match match : latest) {
            Match old = fallback.get(key(match));
            result.add(merge(match, old));
        }
        return result;
    }

    /** Deduplicates fixture responses while letting later complete scores correct earlier ones. */
    static List<Match> dedupeLatest(List<Match> matches) {
        Map<String, Match> unique = new LinkedHashMap<>();
        if (matches != null) for (Match match : matches) {
            String key = key(match);
            Match old = unique.get(key);
            unique.put(key, old == null ? match : merge(match, old));
        }
        return new ArrayList<>(unique.values());
    }

    /** Adds old rows as fallbacks, so incomplete responses cannot drop valid fixtures/results. */
    static List<Match> preserveMissing(List<Match> latest, List<Match> previous) {
        Map<String, Match> unique = new LinkedHashMap<>();
        if (previous != null) for (Match match : previous) unique.put(key(match), match);
        if (latest != null) for (Match match : latest) {
            String key = key(match);
            Match old = unique.get(key);
            unique.put(key, old == null ? match : merge(match, old));
        }
        return new ArrayList<>(unique.values());
    }

    private static Map<String, Match> byFixture(List<Match> matches) {
        Map<String, Match> result = new LinkedHashMap<>();
        if (matches != null) for (Match match : matches) {
            String key = key(match);
            Match old = result.get(key);
            result.put(key, old == null ? match : merge(match, old));
        }
        return result;
    }

    private static String key(Match match) {
        if (match == null) return "";
        return match.fixtureId == null || match.fixtureId.isEmpty() ? "id:" + match.id : "fixture:" + match.fixtureId;
    }
}
