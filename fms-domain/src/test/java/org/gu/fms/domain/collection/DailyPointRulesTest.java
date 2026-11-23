package org.gu.fms.domain.collection;

import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyPointRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    private static Money xaf(long v) {
        return Money.of(v, CurrencyUnit.XAF);
    }

    private static DailyPoint point(long expected, long received, DaySituation s) {
        return DailyPointRules.compute(DAY, 1L, 2L, xaf(expected), xaf(received), s, s == DaySituation.EXEMPT ? "Panne" : null,
                "test", Instant.EPOCH);
    }

    @Test
    void shortageIsADebtAndSurplusOffsetsIt() {
        assertEquals(5_000, DailyPointRules.shortageMovement(point(25_000, 20_000, DaySituation.NORMAL)));
        assertEquals(-3_000, DailyPointRules.shortageMovement(point(25_000, 28_000, DaySituation.NORMAL)));
        assertEquals(0, DailyPointRules.shortageMovement(point(25_000, 25_000, DaySituation.NORMAL)));
    }

    @Test
    void exemptDayExpectsNothingAndNeedsAReason() {
        DailyPoint p = point(25_000, 0, DaySituation.EXEMPT);
        assertEquals(xaf(0), p.expected());
        assertEquals(0, DailyPointRules.shortageMovement(p));
        assertEquals("dailyPoint.exemptReasonRequired",
                DailyPointRules.validate(DaySituation.EXEMPT, "  ").get(0).messageKey());
        assertTrue(DailyPointRules.validate(DaySituation.NORMAL, null).isEmpty());
    }

    @Test
    void totalDueCapsEachRubricAtZero() {
        DriverBalance b = new DriverBalance(1L, xaf(-4_000), xaf(10_000), xaf(2_000));
        assertEquals(xaf(12_000), b.totalDue());
        assertEquals(xaf(4_000), b.credit());
        assertEquals(xaf(0), b.shortageDue());
    }

    @Test
    void collectionIsEditableOnlyOnItsDayBeforePointAndClosing() {
        CollectionEntry c = new CollectionEntry(1L, 0, DAY, Instant.EPOCH, 1L, 2L, 3L, xaf(10_000),
                CollectionKind.COLLECTION, CollectionStatus.VALID, null, 9L, "caisse", null, null);
        assertEquals(null, CollectionRules.lockReason(c, DAY, false, false));
        assertEquals("collection.locked.pastDay", CollectionRules.lockReason(c, DAY.plusDays(1), false, false));
        assertEquals("collection.locked.pointed", CollectionRules.lockReason(c, DAY, true, false));
        assertEquals("collection.locked.closed", CollectionRules.lockReason(c, DAY, false, true));
        assertEquals("collection.locked.credited", CollectionRules.lockReason(c.credited(), DAY, false, false));
        CollectionEntry credit = CollectionRules.creditNoteFor(c, DAY.plusDays(2), Instant.EPOCH, "caisse", "Erreur");
        assertEquals(-10_000, credit.signedMinor());
        assertEquals(DAY.plusDays(2), credit.date());
        assertEquals(1L, credit.originId());
    }
}
