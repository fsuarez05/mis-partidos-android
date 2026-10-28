package uy.federico.mispartidos;

import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import static org.junit.Assert.*;

/** Simulated GOAL payloads and foreground scheduling rules; no real service calls. */
public class LiveRefreshTest {
    private static final long NOW=1_800_000_000_000L;
    private static final long DURATION=AppStore.MATCH_DURATION_MS;

    private JSONObject fixture(String status,long kickoff,String score)throws Exception{
        JSONObject value=new JSONObject().put("status",status).put("kickoffUtc",java.time.Instant.ofEpochMilli(kickoff).toString());
        if(score!=null)value.put("score",new JSONObject(score));
        return value;
    }

    @Test public void scheduledFixtureHasNotStarted(){
        assertEquals(FixtureStatusPolicy.SCHEDULED,FixtureStatusPolicy.classify("Scheduled",NOW+60_000,NOW,DURATION));
    }

    @Test public void scheduledFixtureMovesToLiveAtKickoff(){
        assertEquals(FixtureStatusPolicy.LIVE,FixtureStatusPolicy.classify("Scheduled",NOW-1,NOW,DURATION));
    }

    @Test public void liveScoreCanChangeBetweenSimulatedResponses()throws Exception{
        assertArrayEquals(new int[]{1,0},LiveRefreshPolicy.completeScore(fixture("Live",NOW-60_000,"{\"home\":1,\"away\":0}")));
        assertArrayEquals(new int[]{2,0},LiveRefreshPolicy.completeScore(fixture("Live",NOW-60_000,"{\"home\":2,\"away\":0}")));
    }

    @Test public void finishedFixtureIsClassifiedAndKeepsFinalScore()throws Exception{
        JSONObject response=fixture("Full Time",NOW-DURATION-60_000,"{\"fullTime\":{\"home\":2,\"away\":1}}");
        assertEquals(FixtureStatusPolicy.FINISHED,FixtureStatusPolicy.classify(response.optString("status"),NOW-DURATION-60_000,NOW,DURATION));
        assertArrayEquals(new int[]{2,1},LiveRefreshPolicy.completeScore(response));
    }

    @Test public void postponedAndSuspendedFixturesAreNotLiveOrFinal(){
        assertEquals(FixtureStatusPolicy.POSTPONED,FixtureStatusPolicy.classify("Postponed",NOW-1,NOW,DURATION));
        assertEquals(FixtureStatusPolicy.POSTPONED,FixtureStatusPolicy.classify("Suspended",NOW-1,NOW,DURATION));
        assertFalse(FixtureStatusPolicy.isInProgress(FixtureStatusPolicy.POSTPONED,NOW-1,NOW,DURATION));
    }

    @Test public void multipleFixturesCanBeLiveAtTheSameTime(){
        assertTrue(LiveRefreshPolicy.isLiveCandidate(false,FixtureStatusPolicy.LIVE,NOW-10_000,NOW));
        assertTrue(LiveRefreshPolicy.isLiveCandidate(false,FixtureStatusPolicy.LIVE,NOW-20_000,NOW));
    }

    @Test public void favoriteMatchIdentityMatchesStableGoalIds(){
        assertTrue(TeamIdentity.isFavorite("Arsenal FC","Everton FC","club-1","club-2",
                new HashSet<>(Arrays.asList("Arsenal","Everton")),Collections.singletonMap("Arsenal","club-1")));
    }

    @Test public void selectedCompetitionFixturesAreAcceptedByExistingFilter(){
        assertTrue(ApiClient.competitionSelected("Premier League","England",
                Collections.singleton("Europa › Inglaterra › Premier League"),Collections.emptySet()));
    }

    @Test public void incompleteGoalResponseDoesNotCreateScore()throws Exception{
        JSONObject response=new JSONObject().put("homeTeamScore",1);
        assertNull(LiveRefreshPolicy.completeScore(response));
        assertNull(LiveRefreshPolicy.completeScore(new JSONObject()));
        assertFalse(LiveRefreshPolicy.isUsableFixtureResponse(new JSONObject().put("ok",true).put("data",new JSONObject())));
    }

    @Test public void connectionErrorEnvelopeIsRejected()throws Exception{
        JSONObject error=new JSONObject().put("ok",false).put("error","connection reset");
        assertFalse(LiveRefreshPolicy.isUsableFixtureResponse(error));
    }

    @Test public void failedRequestDoesNotBypassFiveMinuteStartInterval(){
        assertFalse(LiveRefreshPolicy.shouldStart(NOW+4*60_000,NOW,true,true,false,true));
        assertTrue(LiveRefreshPolicy.shouldStart(NOW+5*60_000,NOW,true,true,false,true));
    }

    @Test public void simultaneousRequestIsBlocked(){
        assertFalse(LiveRefreshPolicy.shouldStart(NOW+LiveRefreshPolicy.INTERVAL_MS,NOW,true,true,true,true));
    }

    @Test public void requestsRespectFiveMinuteMinimumBetweenStarts(){
        assertFalse(LiveRefreshPolicy.shouldStart(NOW+LiveRefreshPolicy.INTERVAL_MS-1,NOW,true,true,false,true));
        assertTrue(LiveRefreshPolicy.shouldStart(NOW+LiveRefreshPolicy.INTERVAL_MS,NOW,true,true,false,true));
    }

    @Test public void backgroundStateNeverStartsPolling(){
        assertFalse(LiveRefreshPolicy.shouldStart(NOW+LiveRefreshPolicy.INTERVAL_MS,NOW,false,true,false,true));
    }

    @Test public void noLiveFixturesMeansNoPeriodicRequest(){
        assertFalse(LiveRefreshPolicy.isLiveCandidate(false,FixtureStatusPolicy.SCHEDULED,NOW+60_000,NOW));
        assertFalse(LiveRefreshPolicy.isLiveCandidate(true,FixtureStatusPolicy.LIVE,NOW-60_000,NOW));
        assertFalse(LiveRefreshPolicy.shouldStart(NOW+LiveRefreshPolicy.INTERVAL_MS,NOW,true,false,false,true));
    }

    @Test public void dateSelectionUsesMontevideoCalendar(){
        long utcMidnight=java.time.Instant.parse("2026-10-28T00:30:00Z").toEpochMilli();
        assertEquals(java.time.LocalDate.of(2026,10,27),MontevideoTime.date(utcMidnight));
    }
}
