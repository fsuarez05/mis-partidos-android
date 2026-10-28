package uy.federico.mispartidos;

import org.junit.Test;
import static org.junit.Assert.*;

public class FixtureStatusPolicyTest {
    private static final long DURATION=135*60_000L;

    @Test public void futureKickoffCannotBecomeFinalFromInconsistentApiStatus() {
        long now=1_800_000_000_000L;
        assertEquals(FixtureStatusPolicy.SCHEDULED,FixtureStatusPolicy.classify("Finished",now+60_000,now,DURATION));
        assertFalse(FixtureStatusPolicy.isFinished("FT",now+60_000,now,DURATION));
    }

    @Test public void explicitLiveStatusesRemainLiveAfterNormalDuration() {
        long now=1_800_000_000_000L,kickoff=now-DURATION-60_000;
        assertEquals(FixtureStatusPolicy.LIVE,FixtureStatusPolicy.classify("Second Half",kickoff,now,DURATION));
        assertEquals(FixtureStatusPolicy.LIVE,FixtureStatusPolicy.classify("LIVE",kickoff,now,DURATION));
    }

    @Test public void finishedStatusesAndElapsedUnknownFixturesAreFinal() {
        long now=1_800_000_000_000L,kickoff=now-DURATION-60_000;
        assertTrue(FixtureStatusPolicy.isFinished("Full Time",kickoff,now,DURATION));
        assertTrue(FixtureStatusPolicy.isFinished("",kickoff,now,DURATION));
    }

    @Test public void postponedFixtureIsNeverShownAsLiveOrFinished() {
        long now=1_800_000_000_000L,kickoff=now-10_000;
        assertEquals(FixtureStatusPolicy.POSTPONED,FixtureStatusPolicy.classify("Postponed",kickoff,now,DURATION));
        assertEquals(FixtureStatusPolicy.POSTPONED,FixtureStatusPolicy.classify("Postponed",kickoff+60_000,now,DURATION));
    }

    @Test public void staleScheduledStatusTransitionsToLiveAtKickoff() {
        long now=1_800_000_000_000L,kickoff=now-30*60_000L;
        assertTrue(FixtureStatusPolicy.isInProgress(FixtureStatusPolicy.SCHEDULED,kickoff,now,DURATION));
        assertFalse(FixtureStatusPolicy.isInProgress(FixtureStatusPolicy.SCHEDULED,kickoff+DURATION+1,now,DURATION));
    }
}
