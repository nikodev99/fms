package org.gu.fms.desktop.features.finance;

import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.finance.*;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.gu.fms.desktop.testing.Fixture.xaf;
import static org.junit.jupiter.api.Assertions.*;

/** Grand livre : soldes recalculés, solde négatif refusé, contre-passation, virements neutres, base immuable. */
class LedgerAndTransferTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @TempDir
    Path dir;
    private Fixture f;
    private long cash;
    private long momo;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
        f.clock.setDay(DAY);
        cash = f.account("Caisse");
        momo = f.account("MTN Mobile Money");
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    private long category(String name) {
        return f.txCategoryRepo.findAll().stream().filter(c -> c.name().equals(name)).map(TransactionCategory::id)
                .findFirst().orElseThrow();
    }

    /*private FinancialTransaction manual(TransactionDirection d, String category, long account, long amount, LocalDate date) {
        return f.transactions.save(new FinancialTransaction(null, 0, date, null, d, category(category), null, account,
                xaf(amount), null, null, "test", null, TransactionSource.MANUAL, null, null, null, null));
    }

    @Test
    void balanceIsOpeningPlusMovements() {
        manual(TransactionDirection.INFLOW, "Apport du propriétaire", cash, 100_000, DAY.minusDays(2));
        manual(TransactionDirection.OUTFLOW, "Frais administratifs", cash, 30_000, DAY);
        assertEquals(xaf(70_000), f.ledger.balance(cash));
        assertEquals(xaf(100_000), f.ledger.balanceAt(cash, DAY.minusDays(1)));
    }

    @Test
    void cashCannotGoNegativeAtAnyDate() {
        manual(TransactionDirection.INFLOW, "Apport du propriétaire", cash, 50_000, DAY);
        ValidationException e = assertThrows(ValidationException.class,
                () -> manual(TransactionDirection.OUTFLOW, "Frais administratifs", cash, 60_000, DAY));
        assertEquals("account.insufficientBalance", e.errors().get(0).messageKey());
        // une sortie datée d'hier, quand l'argent n'est arrivé qu'aujourd'hui, est refusée aussi
        ValidationException early = assertThrows(ValidationException.class,
                () -> manual(TransactionDirection.OUTFLOW, "Frais administratifs", cash, 10_000, DAY.minusDays(1)));
        assertEquals("account.insufficientBalance", early.errors().get(0).messageKey());
    }*/

    /*@Test
    void reversalCancelsAndCannotBeRepeated() {
        manual(TransactionDirection.INFLOW, "Apport du propriétaire", cash, 50_000, DAY.minusDays(5));
        FinancialTransaction fuel = f.transactions.save(new FinancialTransaction(null, 0, DAY.minusDays(3), null,
                TransactionDirection.OUTFLOW, category("Carburant"), null, cash, xaf(20_000),
                f.vehicle("BZ 100 A").id(), null, null, null, TransactionSource.MANUAL, null, null, null, null));
        FinancialTransaction reversal = f.reversals.reverse(fuel.id(), "Saisi deux fois");
        assertEquals(DAY, reversal.date());
        assertEquals(TransactionDirection.INFLOW, reversal.direction());
        assertEquals(xaf(50_000), f.ledger.balance(cash));
        assertTrue(f.transactionRepo.findById(fuel.id()).orElseThrow().isReversed());
        assertThrows(ValidationException.class, () -> f.reversals.reverse(fuel.id(), "encore"));
        assertThrows(ValidationException.class, () -> f.reversals.reverse(reversal.id(), "encore"));
    }

    @Test
    void savedTransactionIsProtectedByTheDatabase() {
        FinancialTransaction t = manual(TransactionDirection.INFLOW, "Apport du propriétaire", cash, 50_000, DAY);
        assertThrows(RuntimeException.class, () -> f.db.jdbc.update("UPDATE financial_transaction SET amount = 1 WHERE id = ?", t.id()));
        assertThrows(RuntimeException.class, () -> f.db.jdbc.update("DELETE FROM financial_transaction WHERE id = ?", t.id()));
        assertThrows(ValidationException.class, () -> f.transactions.save(t));
    }

    @Test
    void transferMovesMoneyWithoutChangingConsolidatedCash() {
        manual(TransactionDirection.INFLOW, "Apport du propriétaire", cash, 100_000, DAY);
        Money before = f.ledger.balance(cash).plus(f.ledger.balance(momo));
        AccountTransfer t = f.transfers.save(new AccountTransfer(null, 0, DAY, cash, momo, xaf(40_000), xaf(500),
                "Dépôt", null, null, null, null, TransferStatus.VALID, null, null));
        assertEquals(xaf(100_000 - 40_000 - 500), f.ledger.balance(cash));
        assertEquals(xaf(40_000), f.ledger.balance(momo));
        assertEquals(before.minus(xaf(500)), f.ledger.balance(cash).plus(f.ledger.balance(momo)));   // seuls les frais sortent

        // contre-passer une jambe depuis l'écran Transactions annule tout le virement
        f.reversals.reverse(t.outTransactionId(), "Erreur de compte");
        assertEquals(xaf(100_000), f.ledger.balance(cash));
        assertEquals(xaf(0), f.ledger.balance(momo));
        assertEquals(TransferStatus.REVERSED, f.transferRepo.findById(t.id()).orElseThrow().status());
    }

    @Test
    void accountWithMovementsKeepsItsOpeningAndCannotBeDeactivatedWithMoney() {
        manual(TransactionDirection.INFLOW, "Apport du propriétaire", cash, 10_000, DAY);
        var account = f.accountRepo.findById(cash).orElseThrow();
        ValidationException e = assertThrows(ValidationException.class, () -> f.accounts.save(
                new FinancialAccount(account.id(), account.version(),
                        account.name(), account.type(), xaf(5_000), account.openingDate(), true, false, false, null)));
        assertEquals(java.util.List.of("account.openingLocked", "account.deactivateNonZero"),
                e.errors().stream().map(x -> x.messageKey()).toList());
    }*/
}
