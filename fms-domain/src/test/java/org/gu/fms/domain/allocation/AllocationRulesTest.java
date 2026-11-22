package org.gu.fms.domain.allocation;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AllocationRulesTest {

    private static AllocationRule rule(Long id, AllocationBasis basis, String rate, Long amount, boolean active) {
        return new AllocationRule(id, 0, " Entretien ", AllocationPurpose.MAINTENANCE, basis,
                rate == null ? null : Percent.of(rate), amount == null ? null : Money.of(amount, CurrencyUnit.XAF),
                active, "15 % des versements encaissés");
    }

    @Test
    void keepsOnlyTheValueMatchingTheBasis() {
        AllocationRule r = AllocationRules.normalize(rule(null, AllocationBasis.COLLECTIONS_PERCENT, "15", 5_000L, true));
        assertNull(r.amount());
        assertEquals("Entretien", r.name());
        assertTrue(AllocationRules.validate(r).isEmpty());
        AllocationRule fixed = AllocationRules.normalize(rule(null, AllocationBasis.FIXED_MONTHLY, "15", null, true));
        assertEquals("amount", AllocationRules.validate(fixed).get(0).field());
    }

    @Test
    void totalOnSameBasisExcludesInactiveAndReplacesEditedRule() {
        List<AllocationRule> saved = List.of(
                rule(1L, AllocationBasis.COLLECTIONS_PERCENT, "60", null, true),
                rule(2L, AllocationBasis.COLLECTIONS_PERCENT, "30", null, false),
                rule(3L, AllocationBasis.NET_COLLECTIONS_PERCENT, "50", null, true));
        AllocationRule candidate = rule(null, AllocationBasis.COLLECTIONS_PERCENT, "40", null, true);
        assertEquals(new BigDecimal("100.00"), AllocationRules.totalForBasis(candidate, saved));
        assertFalse(AllocationRules.exceedsHundred(AllocationRules.totalForBasis(candidate, saved)));
        AllocationRule edited = rule(1L, AllocationBasis.COLLECTIONS_PERCENT, "70", null, true);
        AllocationRule another = rule(null, AllocationBasis.COLLECTIONS_PERCENT, "31", null, true);
        List<AllocationRule> afterEdit = List.of(edited, saved.get(1), saved.get(2));
        assertTrue(AllocationRules.exceedsHundred(AllocationRules.totalForBasis(another, afterEdit)));
    }
}