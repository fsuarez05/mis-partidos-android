package uy.federico.mispartidos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

final class MontevideoTime {
    static final ZoneId ZONE = ZoneId.of("America/Montevideo");
    private MontevideoTime() {}
    static LocalDate date(long epochMillis) { return Instant.ofEpochMilli(epochMillis).atZone(ZONE).toLocalDate(); }
}
