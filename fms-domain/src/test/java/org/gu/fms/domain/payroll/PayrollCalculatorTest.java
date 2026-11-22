package org.gu.fms.domain.payroll;

import org.gu.fms.domain.collection.DriverBalance;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.MaritalStatus;
import org.gu.fms.domain.driver.PayScheme;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calcul de paie. Valeurs attendues identiques aux tests de spécification de l'édition Excel
 * (ITS : 400 000 → 35 575 / mois ; 80 000 → 1 117 / mois) et recalculées à la main :
 * 400 000 − 4 % CNSS = 384 000 ; × 80 % × 12 = 3 686 400 → 3 686 000 ;
 * 1 200 + 88 500 + 300 000 + 37 200 = 426 900 / an → 35 575 / mois.
 */
class PayrollCalculatorTest {

    private static final CurrencyUnit XAF = CurrencyUnit.XAF;
    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private static final LocalDate EFFECT = LocalDate.of(2023, 1, 1);
    private static final ItsSettings ITS_ON = new ItsSettings(true, Percent.of("20"), xaf(1_200), "LF 2026");

    private static Money xaf(long v) {
        return Money.of(v, XAF);
    }

    static List<PayrollContribution> cnss() {
        return List.of(
                line("CNSS_PENSION_EMPLOYEE", ContributionGroup.CNSS, ContributionPayer.EMPLOYEE, "4", 1_200_000, true),
                line("CNSS_PENSION_EMPLOYER", ContributionGroup.CNSS, ContributionPayer.EMPLOYER, "8", 1_200_000, true),
                line("CNSS_FAMILY", ContributionGroup.CNSS, ContributionPayer.EMPLOYER, "10.03", 600_000, true),
                line("CNSS_WORK_ACCIDENT", ContributionGroup.CNSS, ContributionPayer.EMPLOYER, "2.25", 600_000, true),
                line("TUS", ContributionGroup.PAYROLL_TAX, ContributionPayer.EMPLOYER, "7.5", 0, false));
    }

    private static PayrollContribution line(String code, ContributionGroup g, ContributionPayer p, String rate,
                                            long ceiling, boolean enabled) {
        return new PayrollContribution(null, 0, code, code, g, p, Percent.of(rate), ceiling == 0 ? null : xaf(ceiling),
                null, enabled, EFFECT, "test", false);
    }

    private static Driver driver(PayScheme scheme, Money fixed, Percent rate, boolean cnss) {
        return new Driver(1L, 0, "MBEMBA", "Jean", "06 474 50 67", null, null, null, null, null, Driver.DriverStatus.ACTIVE,
                scheme, fixed, rate, null, null, cnss, null, true, MaritalStatus.SINGLE, 0, null);
    }

    private static PayrollResult calc(Driver d, long base, ItsSettings its, DriverBalance balance, RecoveryPolicy policy,
                                      Recoveries requested) {
        return PayrollCalculator.calculate(new PayrollInput(NOV, d, xaf(base), xaf(0), cnss(), its,
                ItsScaleTest.lf2026(), balance, policy, requested));
    }

    @Test
    void fixedSalaryWithCnssAndItsMatchesExcelEdition() {
        PayrollResult r = calc(driver(PayScheme.FIXED, xaf(400_000), null, true), 0, ITS_ON,
                DriverBalance.zero(1L, XAF), RecoveryPolicy.excelDefault(), null);
        assertEquals(xaf(400_000), r.gross());
        assertEquals(xaf(16_000), r.employeeContributions());
        assertEquals(xaf(35_575), r.its());
        assertEquals(xaf(348_425), r.netBeforeRecoveries());
        assertEquals(xaf(348_425), r.netPayable());
        // 8 % × 400 000 + 10,03 % × 400 000 + 2,25 % × 400 000 ; TUS désactivée
        assertEquals(xaf(32_000 + 40_120 + 9_000), r.employerCharges());
        assertEquals(xaf(481_120), r.employerCost());
        assertEquals(4, r.lines().size());
    }

    @Test
    void lowSalaryItsMatchesExcelEdition() {
        PayrollResult r = calc(driver(PayScheme.FIXED, xaf(80_000), null, true), 0, ITS_ON,
                DriverBalance.zero(1L, XAF), RecoveryPolicy.excelDefault(), null);
        assertEquals(xaf(3_200), r.employeeContributions());
        assertEquals(xaf(1_117), r.its());                // 13 400 / 12 = 1 116,67 → arrondi commercial
    }

    @Test
    void ceilingsApplyPerContribution() {
        PayrollResult r = calc(driver(PayScheme.FIXED, xaf(1_500_000), null, true), 0, null,
                DriverBalance.zero(1L, XAF), RecoveryPolicy.excelDefault(), null);
        assertEquals(xaf(48_000), r.employeeContributions());              // 4 % × 1 200 000
        assertEquals(xaf(96_000 + 60_180 + 13_500), r.employerCharges());   // 8 % × 1,2 M + 10,03 % et 2,25 % × 600 000
        assertEquals(xaf(0), r.its());
    }

    @Test
    void driverNotDeclaredHasNoCnssAndItsOffByDefault() {
        PayrollResult r = calc(driver(PayScheme.PERCENTAGE, null, Percent.of("15"), false), 523_457,
                new ItsSettings(false, Percent.of("20"), xaf(1_200), "x"), DriverBalance.zero(1L, XAF),
                RecoveryPolicy.excelDefault(), null);
        assertEquals(xaf(78_519), r.commission());          // 78 518,55 → arrondi commercial
        assertEquals(xaf(78_519), r.gross());
        assertTrue(r.lines().isEmpty());
        assertEquals(xaf(78_519), r.netPayable());
        assertEquals(xaf(78_519), r.employerCost());
    }

    @Test
    void recoveriesFollowOrderAndCap() {
        DriverBalance balance = new DriverBalance(1L, xaf(400_000), xaf(50_000), xaf(10_000));
        RecoveryPolicy capped = new RecoveryPolicy(true, true, true, Percent.of("30"));
        PayrollResult r = calc(driver(PayScheme.FIXED, xaf(400_000), null, true), 0, ITS_ON, balance, capped, null);
        // net 348 425 ; avance d'abord (50 000) ; manquants + dettes plafonnés à 30 % du net = 104 527
        assertEquals(new Recoveries(xaf(50_000), xaf(104_527), xaf(0)), r.recoveries());
        assertEquals(xaf(348_425 - 50_000 - 104_527), r.netPayable());
    }

    @Test
    void negativeShortageBalanceIsACreditNotARecovery() {
        DriverBalance balance = new DriverBalance(1L, xaf(-5_000), xaf(0), xaf(0));
        PayrollResult r = calc(driver(PayScheme.FIXED, xaf(100_000), null, false), 0, null, balance,
                RecoveryPolicy.excelDefault(), null);
        assertEquals(Recoveries.zero(XAF), r.recoveries());
    }

    @Test
    void requestedRecoveriesCanOnlyBeLowered() {
        DriverBalance balance = new DriverBalance(1L, xaf(30_000), xaf(0), xaf(0));
        PayrollResult proposed = calc(driver(PayScheme.FIXED, xaf(100_000), null, false), 0, null, balance,
                RecoveryPolicy.excelDefault(), null);
        assertEquals(xaf(30_000), proposed.maxRecoveries().shortage());
        assertTrue(PayrollCalculator.validateRequested(new Recoveries(xaf(0), xaf(10_000), xaf(0)),
                proposed.maxRecoveries()).isEmpty());
        assertEquals("payroll.recovery.aboveMax", PayrollCalculator.validateRequested(
                new Recoveries(xaf(0), xaf(40_000), xaf(0)), proposed.maxRecoveries()).get(0).messageKey());
        PayrollResult lowered = calc(driver(PayScheme.FIXED, xaf(100_000), null, false), 0, null, balance,
                RecoveryPolicy.excelDefault(), new Recoveries(xaf(0), xaf(10_000), xaf(0)));
        assertEquals(xaf(90_000), lowered.netPayable());
    }

    @Test
    void scaleInForceIsTheLatestNotAfterPeriodStart() {
        List<ItsBand> bands = new java.util.ArrayList<>(ItsScaleTest.lf2026());
        bands.add(new ItsBand(null, 0, LocalDate.of(2027, 1, 1), null, Percent.of("1"), null));
        assertEquals(5, PayrollCalculator.bandsInForce(bands, NOV.atDay(1)).size());
        assertEquals(1, PayrollCalculator.bandsInForce(bands, LocalDate.of(2027, 2, 1)).size());
        assertTrue(PayrollCalculator.bandsInForce(bands, LocalDate.of(2025, 6, 1)).isEmpty());
    }
}
