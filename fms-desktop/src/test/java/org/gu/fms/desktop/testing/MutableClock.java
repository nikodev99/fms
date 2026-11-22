package org.gu.fms.desktop.testing;

import java.time.*;

/** Horloge de test réglable : pour simuler des journées successives (point, clôture, paie). */
public final class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    /** Se place à 9 h (heure de Brazzaville) le jour donné. */
    public void setDay(LocalDate day) {
        now = day.atTime(9, 0).atZone(ZoneId.of("Africa/Brazzaville")).toInstant();
    }

    public void advance(Duration d) {
        now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
