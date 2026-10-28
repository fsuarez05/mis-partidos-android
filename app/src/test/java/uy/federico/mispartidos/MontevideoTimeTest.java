package uy.federico.mispartidos;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class MontevideoTimeTest {
    @Test public void kickoffAndCurrentTimeUseUruguayLocalDate() {
        long instant=Instant.parse("2026-10-24T15:57:00Z").toEpochMilli();
        assertEquals("2026-10-24",MontevideoTime.date(instant).toString());
        assertEquals(12,Instant.ofEpochMilli(instant).atZone(MontevideoTime.ZONE).getHour());
    }
}
