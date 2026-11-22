package org.gu.fms.domain.finance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final TransactionCategory FUEL = new TransactionCategory(5L, 0, null, "Carburant",
            TransactionDirection.OUTFLOW, TransactionNature.OPERATING, true, true, false, true);

    private static FinancialTransaction tx(TransactionDirection d, Long vehicle, LocalDate date) {
        return new FinancialTransaction(null, 0, date, null, d, 5L, null, 1L, Money.of(15_000, CurrencyUnit.XAF),
                vehicle, null, null, null, TransactionSource.MANUAL, null, null, null, null);
    }

    private static List<String> keys(List<FieldError> errors) {
        return errors.stream().map(e -> e.field() + ":" + e.messageKey()).toList();
    }

    @Test
    void categoryMustMatchDirectionAndVehicleWhenRequired() {
        assertTrue(TransactionRules.validate(tx(TransactionDirection.OUTFLOW, 3L, TODAY), FUEL, TODAY).isEmpty());
        assertEquals(List.of("categoryId:transaction.category.wrongDirection", "vehicleId:transaction.vehicle.required"),
                keys(TransactionRules.validate(tx(TransactionDirection.INFLOW, null, TODAY), FUEL, TODAY)));
        assertEquals(List.of("date:validation.notInFuture"),
                keys(TransactionRules.validate(tx(TransactionDirection.OUTFLOW, 3L, TODAY.plusDays(1)), FUEL, TODAY)));
    }

    @Test
    void reversalMirrorsTheOriginalDatedToday() {
        FinancialTransaction original = tx(TransactionDirection.OUTFLOW, 3L, TODAY.minusDays(20)).withIdentity(42L, 0);
        FinancialTransaction r = TransactionRules.reversalOf(original, TODAY, Instant.EPOCH, "u", "Doublon");
        assertEquals(TransactionDirection.INFLOW, r.direction());
        assertEquals(TODAY, r.date());
        assertEquals(42L, r.reversalOfId());
        assertEquals(-original.signedMinor(), r.signedMinor());
        assertTrue(TransactionRules.validate(r, FUEL, TODAY).isEmpty());   // le sens opposé est admis pour une contre-passation
        FinancialTransaction marked = new FinancialTransaction(42L, 1, original.date(), null, original.direction(), 5L,
                null, 1L, original.amount(), 3L, null, null, null, TransactionSource.MANUAL, null, null, 43L, null);
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.reversalOf(marked, TODAY, Instant.EPOCH, "u", "x"));
    }

    @Test
    void closingFiguresKeepTransfersApart() {
        Money z = Money.zero(CurrencyUnit.XAF);
        ClosingFigures f = new ClosingFigures(Money.of(10_000, CurrencyUnit.XAF), Money.of(50_000, CurrencyUnit.XAF),
                Money.of(8_000, CurrencyUnit.XAF), z, Money.of(30_000, CurrencyUnit.XAF));
        assertEquals(Money.of(22_000, CurrencyUnit.XAF), f.expected());
    }
}
