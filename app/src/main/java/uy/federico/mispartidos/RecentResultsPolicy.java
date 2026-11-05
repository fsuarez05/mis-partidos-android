package uy.federico.mispartidos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Keep the last verified score per selected favorite, not an indefinitely
 * growing list of every recent fixture. Multiple favorites in one fixture
 * count as the latest match for both.
 *
 * No network requests or changes to GOAL API polling.
 */
final class RecentResultsPolicy {
    static final long HISTORY_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L;

    private RecentResultsPolicy() { }

    static List<Match> latestPerFavorite(List<Match> candidates,
                                          Function<Match, Set<String>> favoritesFor,
                                          long now) {
        List<Match> sorted=new ArrayList<>();
        if(candidates!=null)for(Match match:candidates) {
            if(match==null || !match.hasScore() || match.kickoff>now ||
                    match.kickoff<now-HISTORY_WINDOW_MS)continue;
            if(!FixtureStatusPolicy.FINISHED.equals(match.status) &&
                    match.kickoff+AppStore.MATCH_DURATION_MS>now)continue;
            sorted.add(match);
        }
        // The newest completed fixture wins for each favorite. Prefer a result
        // with a known score on ties (all entries here have a score).
        sorted.sort(Comparator.comparingLong((Match m)->m.kickoff).reversed());
        List<Match> shown=new ArrayList<>();
        Set<String> represented=new HashSet<>();
        Set<String> fixtures=new HashSet<>();
        for(Match match:sorted) {
            Set<String> favoriteKeys=favoritesFor.apply(match);
            if(favoriteKeys==null || favoriteKeys.isEmpty())continue;
            boolean addsFavorite=false;
            for(String favorite:favoriteKeys)if(!represented.contains(favorite)) {
                addsFavorite=true;
                break;
            }
            if(!addsFavorite)continue;
            // Duplicate entries from team and calendar feeds must only show once.
            String fixtureKey=match.fixtureId.isEmpty()?"":match.fixtureId;
            if(!fixtureKey.isEmpty() && !fixtures.add(fixtureKey)) {
                represented.addAll(favoriteKeys);
                continue;
            }
            shown.add(match);
            represented.addAll(favoriteKeys);
        }
        return shown;
    }
}
