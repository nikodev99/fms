package org.gu.fms.desktop.features.payroll;

import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.allocation.AllocationBasis;
import org.gu.fms.domain.allocation.AllocationPurpose;
import org.gu.fms.domain.allocation.AllocationRule;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.payroll.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.gu.fms.desktop.testing.Fixture.xaf;
import static org.junit.jupiter.api.Assertions.*;

/** Valeurs livrées par V004 et leur cohérence avec le domaine. */
class PayrollSettingsTest {

    @TempDir
    Path dir;
    private Fixture f;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    @Test
    void seededItsScaleIsCompleteAndMatchesArticle116G() {
        List<ItsBand> bands = f.itsBands.findAll();
        assertEquals(5, bands.size());
        assertTrue(f.itsBands.completeness().isEmpty());
        ItsSettings its = f.itsSettings.load();
        assertFalse(its.enabled(), "désactivé par défaut, comme l'édition Excel");
        assertEquals(Percent.of("20"), its.abatement());
        ItsScale scale = ItsScale.of(bands, CurrencyUnit.XAF);
        // 150 000 F/mois, célibataire : voir ItsScaleTest (fms-domain)
        assertEquals(xaf(77_900), scale.annualTax(ItsScale.netTaxable(xaf(1_800_000), xaf(72_000), its.abatement()),
                BigDecimal.ONE, its.minimumTax()));
    }

    @Test
    void seededContributionsCarrySourceAndNewRateIsANewDatedLine() {
        List<PayrollContribution> all = f.contributions.findAll();
        assertEquals(7, all.size());
        assertTrue(all.stream().allMatch(c -> c.legalSource() != null && !c.verified()));
        PayrollContribution pension = PayrollContributionRules.inForce(all, "CNSS_PENSION_EMPLOYEE",
                LocalDate.of(2026, 11, 5)).orElseThrow();
        assertEquals(Percent.of("4"), pension.rate());

        f.contributions.save(new PayrollContribution(null, 0, pension.code(), pension.name(), pension.group(),
                pension.payer(), Percent.of("4.5"), pension.monthlyCeiling(), null, true, LocalDate.of(2027, 1, 1),
                "Test", false));
        List<PayrollContribution> after = f.contributions.findAll();
        assertEquals(Percent.of("4"), PayrollContributionRules.inForce(after, pension.code(),
                LocalDate.of(2026, 12, 31)).orElseThrow().rate());
        assertEquals(Percent.of("4.5"), PayrollContributionRules.inForce(after, pension.code(),
                LocalDate.of(2027, 1, 1)).orElseThrow().rate());

        ValidationException codeFixed = assertThrows(ValidationException.class, () -> f.contributions.save(
                new PayrollContribution(pension.id(), pension.version(), "AUTRE", pension.name(), pension.group(),
                        pension.payer(), pension.rate(), pension.monthlyCeiling(), null, true, pension.effectiveFrom(),
                        pension.legalSource(), false)));
        assertEquals("contribution.code.fixed", codeFixed.errors().get(0).messageKey());
    }

    @Test
    void itsBandEditKeepsScaleConsistent() {
        ItsBand first = f.itsBands.findAll().stream().filter(b -> b.fixedAmount() != null).findFirst().orElseThrow();
        ValidationException duplicate = assertThrows(ValidationException.class, () -> f.itsBands.save(new ItsBand(
                first.id(), first.version(), first.effectiveFrom(), xaf(1_500_000), null, first.fixedAmount())));
        assertEquals("its.band.duplicate", duplicate.errors().get(0).messageKey());
        ValidationException both = assertThrows(ValidationException.class, () -> f.itsBands.save(new ItsBand(
                first.id(), first.version(), first.effectiveFrom(), first.upTo(), Percent.of("5"), first.fixedAmount())));
        assertEquals("its.band.rateOrFixed", both.errors().get(0).messageKey());
        assertTrue(f.itsBands.completeness().isEmpty(), "rien n'a été écrit");
    }

    @Test
    void provisionsOnSameBasisCannotExceedHundredPercent() {
        f.allocations.save(new AllocationRule(null, 0, "Entretien", AllocationPurpose.MAINTENANCE,
                AllocationBasis.COLLECTIONS_PERCENT, Percent.of("15"), null, true, "15 % des versements"));
        f.allocations.save(new AllocationRule(null, 0, "Revenu propriétaire", AllocationPurpose.OWNER_INCOME,
                AllocationBasis.COLLECTIONS_PERCENT, Percent.of("85"), null, true, "Le reste"));
        ValidationException e = assertThrows(ValidationException.class, () -> f.allocations.save(new AllocationRule(
                null, 0, "Assurance", AllocationPurpose.INSURANCE_DOCUMENTS, AllocationBasis.COLLECTIONS_PERCENT,
                Percent.of("1"), null, true, "1 %")));
        assertEquals("allocation.totalExceeds", e.errors().get(0).messageKey());
        assertEquals("101", e.errors().get(0).args().get(0));
    }
}
