package org.gu.fms.desktop.features.collection;

import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.collection.CollectionEntry;
import org.gu.fms.domain.collection.CollectionKind;
import org.gu.fms.domain.collection.CollectionStatus;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.driver.DriverAssignment.AssignmentRole;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.payroll.Payslip;
import org.gu.fms.domain.payroll.PayslipStatus;
import org.gu.fms.domain.vehicle.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.gu.fms.desktop.testing.Fixture.xaf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Paie de bout en bout : commission sur les versements reçus, avance et manquant retenus, retenue diminuée avec
 * note, validation, paiement, contre-passation du paiement, annulation. Plus le paiement d'une intervention.
 */
class PayrollFlowTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @TempDir
    Path dir;
    private Fixture f;
    private long cash;
    private Driver jean;
    private Vehicle car;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
        cash = f.account("Caisse");
        car = f.vehicle("BZ 300 A");
        jean = f.driver("MBEMBA", "P-9");               // 15 % des versements, non déclaré CNSS
        f.assignments.save(new DriverAssignment(null, 0, jean.id(), car.id(), AssignmentRole.PRIMARY,
                LocalDate.of(2026, 11, 1), null, xaf(25_000), null));
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    private void collect(long amount, LocalDate day) {
        f.clock.setDay(day);
        f.collections.save(new CollectionEntry(null, 0, day, null, jean.id(), car.id(), cash, xaf(amount),
                CollectionKind.COLLECTION, CollectionStatus.VALID, null, null, null, null, null));
    }

    @Test
    void monthlyPayroll() {
        collect(25_000, LocalDate.of(2026, 11, 2));
        f.dailyPoints.validate(LocalDate.of(2026, 11, 2), Map.of());
        collect(20_000, LocalDate.of(2026, 11, 3));                         // manquant de 5 000
        f.dailyPoints.validate(LocalDate.of(2026, 11, 3), Map.of());
        f.advances.grant(jean.id(), cash, LocalDate.of(2026, 11, 3), xaf(4_000), null);
        assertEquals(xaf(41_000), f.ledger.balance(cash));
        assertEquals(xaf(5_000), f.driverLedger.balance(jean.id()).shortageDue());
        assertEquals(xaf(4_000), f.driverLedger.balance(jean.id()).advanceDue());

        f.clock.setDay(LocalDate.of(2026, 11, 30));
        assertEquals(1, f.payroll.prepare(NOV));
        assertEquals(0, f.payroll.prepare(NOV));                             // pas de doublon
        Payslip draft = f.payroll.findForPeriod(NOV).get(0);
        assertEquals(xaf(45_000), draft.collectionsBase());
        assertEquals(xaf(6_750), draft.gross());                            // 15 % de 45 000
        assertEquals(xaf(4_000), draft.advanceRecovered());
        assertEquals(xaf(2_750), draft.shortageRecovered());                // limité par le net restant
        assertEquals(xaf(0), draft.netPayable());

        // retenue diminuée : note obligatoire
        Payslip lowered = withRecoveries(draft, 4_000, 1_000, null);
        ValidationException noNote = assertThrows(ValidationException.class, () -> f.payroll.save(lowered));
        assertEquals("payroll.recoveryNoteRequired", noNote.errors().get(0).messageKey());
        Payslip saved = f.payroll.save(withRecoveries(draft, 4_000, 1_000, "Accord : 1 000 par mois"));
        assertEquals(xaf(1_750), saved.netPayable());
        Payslip tooMuch = withRecoveries(saved, 4_000, 3_000, "x");
        assertThrows(ValidationException.class, () -> f.payroll.save(tooMuch));

        Payslip validated = f.payroll.validate(saved.id());
        assertEquals(PayslipStatus.VALIDATED, validated.status());
        assertEquals(xaf(0), f.driverLedger.balance(jean.id()).advanceDue());
        assertEquals(xaf(4_000), f.driverLedger.balance(jean.id()).shortageDue());

        Payslip paid = f.payroll.pay(saved.id(), cash, LocalDate.of(2026, 11, 30), "Reçu 12");
        assertEquals(PayslipStatus.PAID, paid.status());
        assertEquals(xaf(41_000 - 1_750), f.ledger.balance(cash));

        // contre-passation du paiement depuis l'écran Transactions : le bulletin redevient « validé »
        f.reversals.reverse(paid.transactionId(), "Payé au mauvais chauffeur");
        assertEquals(PayslipStatus.VALIDATED, f.payslipRepo.findById(saved.id()).orElseThrow().status());
        assertEquals(xaf(41_000), f.ledger.balance(cash));

        // annulation : les retenues reviennent au compte chauffeur
        f.payroll.cancel(saved.id(), "Erreur de période");
        assertEquals(xaf(4_000), f.driverLedger.balance(jean.id()).advanceDue());
        assertEquals(xaf(5_000), f.driverLedger.balance(jean.id()).shortageDue());
        assertEquals(1, f.payroll.prepare(NOV));                             // un nouveau bulletin est possible
    }

    @Test
    void validationRefusedWhenDataChangedSinceDraft() {
        collect(25_000, LocalDate.of(2026, 11, 2));
        f.clock.setDay(LocalDate.of(2026, 11, 20));
        f.payroll.prepare(NOV);
        Payslip draft = f.payroll.findForPeriod(NOV).get(0);
        collect(10_000, LocalDate.of(2026, 11, 20));
        ValidationException changed = assertThrows(ValidationException.class, () -> f.payroll.validate(draft.id()));
        assertEquals("payroll.changedReview", changed.errors().get(0).messageKey());
        assertEquals(xaf(35_000), f.payslipRepo.findById(draft.id()).orElseThrow().collectionsBase());
        assertEquals(PayslipStatus.VALIDATED, f.payroll.validate(draft.id()).status());
    }

    @Test
    void advanceReversalLeavesTheDriverAccount() {
        collect(25_000, LocalDate.of(2026, 11, 2));
        FinancialTransaction advance = f.advances.grant(jean.id(), cash, LocalDate.of(2026, 11, 2), xaf(10_000), null);
        f.reversals.reverse(advance.id(), "Avance non remise");
        assertEquals(xaf(0), f.driverLedger.balance(jean.id()).advanceDue());
        assertEquals(xaf(25_000), f.ledger.balance(cash));
        ValidationException tooMuch = assertThrows(ValidationException.class,
                () -> f.advances.repay(jean.id(), cash, LocalDate.of(2026, 11, 2), xaf(1), null));
        assertEquals("advance.repayAboveDue", tooMuch.errors().get(0).messageKey());
    }

    @Test
    void interventionPaidInPartsAndProtectedWhilePaid() {
        collect(100_000, LocalDate.of(2026, 11, 2));
        MaintenanceEvent e = f.events.save(new MaintenanceEvent(null, 0, car.id(), 1L, MaintenanceEvent.MaintenanceKind.SERVICE,
                LocalDate.of(2026, 11, 2), null, null, "Garage du Plateau", xaf(60_000),
                MaintenanceEvent.MaintenanceEventStatus.RECORDED, null, null));
        FinancialTransaction part = f.maintenancePayments.pay(e.id(), cash, LocalDate.of(2026, 11, 2), xaf(40_000), null);
        assertEquals(xaf(20_000), f.maintenancePayments.remaining(e));
        ValidationException above = assertThrows(ValidationException.class,
                () -> f.maintenancePayments.pay(e.id(), cash, LocalDate.of(2026, 11, 2), xaf(30_000), null));
        assertEquals("maintenance.payment.aboveRemaining", above.errors().get(0).messageKey());

        MaintenanceEvent stored = f.eventRepo.findById(e.id()).orElseThrow();
        MaintenanceEvent cancel = new MaintenanceEvent(stored.id(), stored.version(), stored.vehicleId(), stored.typeId(),
                stored.kind(), stored.date(), null, null, stored.supplier(), stored.cost(), MaintenanceEvent.MaintenanceEventStatus.CANCELLED,
                "Saisie en double", null);
        ValidationException paidAlready = assertThrows(ValidationException.class, () -> f.events.save(cancel));
        assertEquals("maintenance.event.cancelPaid", paidAlready.errors().get(0).messageKey());
        f.reversals.reverse(part.id(), "Paiement erroné");
        assertEquals(MaintenanceEvent.MaintenanceEventStatus.CANCELLED, f.events.save(cancel).status());
        assertNull(f.transactionRepo.findById(part.id()).orElseThrow().reversalOfId());
    }

    private static Payslip withRecoveries(Payslip p, long advance, long shortage, String note) {
        return new Payslip(p.id(), p.version(), p.period(), p.driverId(), p.payScheme(), p.collectionsBase(),
                p.fixedPart(), p.commission(), p.adjustment(), p.adjustmentReason(), p.gross(),
                p.employeeContributions(), p.its(), p.itsParts(), p.netBeforeRecoveries(), xaf(advance), xaf(shortage),
                p.otherDebtRecovered(), note, p.netPayable(), p.employerCharges(), p.employerCost(), p.status(), null,
                null, null, null, null, null, null, p.lines());
    }
}
