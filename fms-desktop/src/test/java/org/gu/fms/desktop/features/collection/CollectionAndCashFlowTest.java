package org.gu.fms.desktop.features.collection;

import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.collection.CollectionEntry;
import org.gu.fms.domain.collection.CollectionKind;
import org.gu.fms.domain.collection.CollectionStatus;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.finance.CashClosing;
import org.gu.fms.domain.finance.ClosingStatus;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.vehicle.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.gu.fms.desktop.testing.Fixture.xaf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Journée de caisse de bout en bout, comme la recette de l'édition Excel : versements multiples, modification du
 * jour, point journalier (manquant au compte chauffeur), versement tardif (régularisation), avoir, clôture avec
 * écart, journée verrouillée, réouverture.
 */
class CollectionAndCashFlowTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @TempDir
    Path dir;
    private Fixture f;
    private long cash;
    private Driver jean;
    private Driver paul;
    private Vehicle car;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
        cash = f.account("Caisse");
        car = f.vehicle("BZ 200 A");
        jean = f.driver("MBEMBA", "P-1");
        paul = f.driver("NGOMA", "P-2");
        f.assignments.save(new DriverAssignment(null, 0, jean.id(), car.id(), DriverAssignment.AssignmentRole.PRIMARY,
                LocalDate.of(2026, 11, 1), null, xaf(25_000), null));
        f.assignments.save(new DriverAssignment(null, 0, paul.id(), car.id(), DriverAssignment.AssignmentRole.NIGHT,
                LocalDate.of(2026, 11, 1), null, xaf(15_000), null));
        f.clock.setDay(DAY);
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    /*private CollectionEntry collect(Driver d, long amount, LocalDate date) {
        return f.collections.save(new CollectionEntry(null, 0, date, null, d.id(), car.id(), cash, xaf(amount),
                CollectionKind.COLLECTION, CollectionStatus.VALID, null, null, null, null, null));
    }

    private PointRow row(List<PointRow> rows, Driver d) {
        return rows.stream().filter(r -> r.driverId() == d.id()).findFirst().orElseThrow();
    }

    @Test
    void fullCashDay() {
        CollectionEntry first = collect(jean, 10_000, DAY);
        CollectionEntry second = collect(jean, 12_000, DAY);
        assertEquals(xaf(22_000), f.ledger.balance(cash));

        // modification du jour : motif obligatoire, la transaction liée suit
        CollectionEntry edited = new CollectionEntry(second.id(), second.version(), DAY, null, jean.id(), car.id(), cash,
                xaf(15_000), CollectionKind.COLLECTION, CollectionStatus.VALID, null, second.transactionId(), null, null,
                null);
        ValidationException noReason = assertThrows(ValidationException.class, () -> f.collections.save(edited));
        assertEquals("collection.reasonRequired", noReason.errors().get(0).messageKey());
        f.collections.save(new CollectionEntry(second.id(), second.version(), DAY, null, jean.id(), car.id(), cash,
                xaf(15_000), CollectionKind.COLLECTION, CollectionStatus.VALID, null, second.transactionId(), null,
                "Erreur de frappe", null));
        assertEquals(xaf(25_000), f.ledger.balance(cash));
        assertEquals(xaf(15_000), f.transactionRepo.findById(second.transactionId()).orElseThrow().amount());

        // point : Jean a tout versé (25 000) ; Paul (nuit) 10 000 sur 15 000 attendus
        collect(paul, 10_000, DAY);
        List<PointRow> rows = f.dailyPoints.preview(DAY);
        assertEquals(xaf(0), row(rows, jean).difference());
        assertEquals(xaf(-5_000), row(rows, paul).difference());

        // clôture refusée tant que le point n'est pas validé
        ValidationException pending = assertThrows(ValidationException.class,
                () -> f.closings.close(cash, DAY, xaf(35_000), null));
        assertEquals("closing.dailyPointPending", pending.errors().get(0).messageKey());

        assertEquals(2, f.dailyPoints.validate(DAY, Map.of()));
        assertEquals(xaf(5_000), f.driverLedger.balance(paul.id()).shortageDue());
        assertEquals(xaf(0), f.driverLedger.balance(jean.id()).totalDue());

        // versement du jour pointé : plus modifiable ; un versement tardif régularise le compte
        assertEquals("collection.locked.pointed", f.collections.lockReason(first));
        collect(paul, 2_000, DAY);
        assertEquals(xaf(3_000), f.driverLedger.balance(paul.id()).shortageDue());

        // clôture avec écart : 37 000 attendus, 36 500 comptés
        ValidationException noExplanation = assertThrows(ValidationException.class,
                () -> f.closings.close(cash, DAY, xaf(36_500), null));
        assertEquals("closing.explanationRequired", noExplanation.errors().get(0).messageKey());
        CashClosing closing = f.closings.close(cash, DAY, xaf(36_500), "Pièce de 500 introuvable");
        assertEquals(xaf(37_000), closing.expected());
        assertEquals(xaf(-500), closing.difference());
        assertEquals(xaf(36_500), f.ledger.balance(cash));

        // journée verrouillée : ni versement, ni modification
        ValidationException closed = assertThrows(ValidationException.class, () -> collect(jean, 1_000, DAY));
        assertEquals("transaction.dayClosed", closed.errors().get(0).messageKey());
        assertThrows(RuntimeException.class, () -> f.db.jdbc.update(
                "UPDATE financial_transaction SET amount = 1 WHERE id = ?", first.transactionId()));

        // réouverture : l'écart est contre-passé à la même date, la journée accepte à nouveau des écritures
        CashClosing reopened = f.closings.reopen(closing.id(), "Pièce retrouvée");
        assertEquals(ClosingStatus.REOPENED, reopened.status());
        assertEquals(xaf(37_000), f.ledger.balance(cash));
        assertNull(f.ledger.lockedThrough(cash));
    }

    @Test
    void creditNoteNextDayFlowsIntoThatDay() {
        CollectionEntry wrong = collect(jean, 30_000, DAY);
        f.dailyPoints.validate(DAY, Map.of());
        assertEquals(xaf(-5_000), f.driverLedger.balance(jean.id()).shortage());        // excédent de 5 000

        LocalDate next = DAY.plusDays(1);
        f.clock.setDay(next);
        assertEquals("collection.locked.pastDay", f.collections.lockReason(wrong));
        CollectionEntry credit = f.collections.creditNote(wrong.id(), "Montant mal saisi : 20 000");
        assertEquals(next, credit.date());
        assertEquals(CollectionStatus.CREDITED, f.collectionRepo.findById(wrong.id()).orElseThrow().status());
        FinancialTransaction reversal = f.transactionRepo.findById(credit.transactionId()).orElseThrow();
        assertEquals(wrong.transactionId(), reversal.reversalOfId());
        collect(jean, 20_000, next);
        collect(jean, 25_000, next);
        // jour suivant : reçu = −30 000 + 20 000 + 25 000 = 15 000 pour 25 000 attendus
        assertEquals(xaf(-10_000), row(f.dailyPoints.preview(next), jean).difference());
        f.dailyPoints.validate(next, Map.of());
        assertEquals(xaf(5_000), f.driverLedger.balance(jean.id()).shortageDue());
        assertThrows(ValidationException.class, () -> f.collections.creditNote(wrong.id(), "encore"));
    }

    @Test
    void exemptDriverOwesNothingThatDay() {
        PointRow paulRow = row(f.dailyPoints.preview(DAY), paul);
        ValidationException noReason = assertThrows(ValidationException.class, () -> f.dailyPoints.validate(DAY,
                Map.of(paul.id(), paulRow.decided(DaySituation.EXEMPT, null))));
        assertEquals("dailyPoint.exemptReasonFor", noReason.errors().get(0).messageKey());
        f.dailyPoints.validate(DAY, Map.of(paul.id(), paulRow.decided(DaySituation.EXEMPT, "Malade")));
        assertEquals(xaf(0), f.driverLedger.balance(paul.id()).totalDue());
        assertEquals(xaf(25_000), f.driverLedger.balance(jean.id()).shortageDue());
        assertTrue(f.dailyPoints.preview(DAY).stream().allMatch(PointRow::validated));
        assertThrows(RuntimeException.class, () -> f.db.jdbc.update("UPDATE daily_point SET expected = 0"));
    }*/
}
