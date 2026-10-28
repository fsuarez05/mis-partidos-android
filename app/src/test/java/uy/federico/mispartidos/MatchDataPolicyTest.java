package uy.federico.mispartidos;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class MatchDataPolicyTest {
    private Match match(long rowId,int home,int away,String status){
        return new Match(rowId,"Peñarol","Nacional","Liga AUF",1_800_000_000_000L,false,
                home,away,"fixture-1","Peñarol","Nacional","home-id","away-id","Uruguay",status);
    }

    @Test public void emptyScoreResponseKeepsPreviouslyValidScore(){
        Match merged=MatchDataPolicy.merge(match(2,-1,-1,FixtureStatusPolicy.LIVE),match(1,1,0,FixtureStatusPolicy.LIVE));
        assertTrue(merged.hasScore());assertEquals(1,merged.homeScore);assertEquals(0,merged.awayScore);
    }

    @Test public void newerCompleteScoreReplacesPreviousScore(){
        Match merged=MatchDataPolicy.merge(match(2,2,0,FixtureStatusPolicy.LIVE),match(1,1,0,FixtureStatusPolicy.LIVE));
        assertEquals(2,merged.homeScore);assertEquals(0,merged.awayScore);
    }

    @Test public void officialCorrectionCanLowerScore(){
        Match merged=MatchDataPolicy.merge(match(2,1,1,FixtureStatusPolicy.FINISHED),match(1,2,1,FixtureStatusPolicy.FINISHED));
        assertEquals(1,merged.homeScore);assertEquals(1,merged.awayScore);
    }

    @Test public void finalFixtureWithIncompleteScoreKeepsLastKnownScore(){
        Match merged=MatchDataPolicy.merge(match(2,-1,-1,FixtureStatusPolicy.FINISHED),match(1,3,2,FixtureStatusPolicy.LIVE));
        assertEquals(3,merged.homeScore);assertEquals(2,merged.awayScore);assertEquals(FixtureStatusPolicy.FINISHED,merged.status);
    }

    @Test public void manualSyncAfterAutomaticRefreshPreservesScore(){
        Match automatic=match(1,2,1,FixtureStatusPolicy.LIVE);
        Match manual=match(1,-1,-1,FixtureStatusPolicy.LIVE);
        assertEquals(automatic.homeScore,MatchDataPolicy.merge(manual,automatic).homeScore);
        assertEquals(automatic.awayScore,MatchDataPolicy.merge(manual,automatic).awayScore);
    }

    @Test public void favoriteAndCompetitionCopiesDeduplicateWithoutLosingLatestScore(){
        List<Match> merged=MatchDataPolicy.dedupeLatest(Arrays.asList(
                match(1,1,0,FixtureStatusPolicy.LIVE),match(2,-1,-1,FixtureStatusPolicy.LIVE),
                match(3,2,0,FixtureStatusPolicy.LIVE)));
        assertEquals(1,merged.size());assertEquals(2,merged.get(0).homeScore);assertEquals(0,merged.get(0).awayScore);
    }

    @Test public void missingFixtureInPartialResponseDoesNotDeleteSavedResult(){
        List<Match> merged=MatchDataPolicy.preserveMissing(Collections.emptyList(),Collections.singletonList(
                match(1,2,0,FixtureStatusPolicy.FINISHED)));
        assertEquals(1,merged.size());assertTrue(merged.get(0).hasScore());
    }

    @Test public void partialDuplicateListCannotClearStoredScore(){
        List<Match> merged=MatchDataPolicy.preserveMissing(Collections.singletonList(
                match(2,-1,-1,FixtureStatusPolicy.FINISHED)),Collections.singletonList(
                match(1,4,1,FixtureStatusPolicy.FINISHED)));
        assertEquals(1,merged.size());assertEquals(4,merged.get(0).homeScore);assertEquals(1,merged.get(0).awayScore);
    }
}
