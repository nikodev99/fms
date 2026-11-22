package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.finance.*;
import org.gu.fms.domain.money.Money;

import java.util.List;
import java.util.Map;

/**
 * Virements internes : sortie du compte source + entrée du compte destination (+ frais éventuels), dans une seule
 * transaction SQL. Annulation : les écritures sont contre-passées ensemble, datées du jour.
 */
@Singleton
public final class TransferService implements CrudService<AccountTransfer> {

    static final String AUDIT_TYPE = "account_transfer";

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final Ledger ledger;
    private final TransactionManager tx;
    private final AuditLog audit;

    @Inject
    public TransferService(TransferRepository transfers, AccountRepository accounts, CategoryRepository categories,
                           Ledger ledger, TransactionManager tx, AuditLog audit) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.categories = categories;
        this.ledger = ledger;
        this.tx = tx;
        this.audit = audit;
    }

    @Override
    public List<AccountTransfer> findAll() {
        return transfers.findAll();
    }

    @Override
    public AccountTransfer save(AccountTransfer input) {
        if (!input.isNew()) {
            throw ValidationException.of(FieldError.of(null, "transfer.immutable"));
        }
        ValidationException.throwIfAny(TransferRules.validate(input, ledger.today()));
        return tx.inTransaction(c -> {
            String from = accounts.findById(input.fromAccountId()).map(a -> a.name()).orElse("?");
            String to = accounts.findById(input.toAccountId()).map(a -> a.name()).orElse("?");
            String label = TextRules.normalizeSpaces(input.label());
            AccountTransfer draft = new AccountTransfer(null, 0, input.date(), input.fromAccountId(),
                    input.toAccountId(), input.amount(), input.hasFee() ? input.fee() : null, label,
                    TextRules.normalizeSpaces(input.reference()), null, null, null, TransferStatus.VALID, null,
                    ledger.user());
            long id = transfers.insert(draft);
            String text = label != null ? label : from + " → " + to;
            FinancialTransaction out = ledger.post(leg(id, draft, TransactionDirection.OUTFLOW, SystemCategory.TRANSFER_OUT,
                    draft.fromAccountId(), draft.amount(), text));
            FinancialTransaction in = ledger.post(leg(id, draft, TransactionDirection.INFLOW, SystemCategory.TRANSFER_IN,
                    draft.toAccountId(), draft.amount(), text));
            Long feeId = null;
            if (draft.hasFee()) {
                feeId = ledger.post(leg(id, draft, TransactionDirection.OUTFLOW, SystemCategory.BANK_FEES,
                        draft.fromAccountId(), draft.fee(), text)).id();
            }
            AccountTransfer linked = draft.withIdentity(id, 0).withTransactions(out.id(), in.id(), feeId);
            long version = transfers.update(linked);
            AccountTransfer saved = linked.withIdentity(id, version);
            audit.created(AUDIT_TYPE, id, transfers.snapshot(saved));
            return saved;
        });
    }

    /** Annule le virement : ses écritures sont contre-passées aujourd'hui (le compte destination doit avoir les fonds). */
    public AccountTransfer reverse(long transferId, String reason) {
        return tx.inTransaction(c -> {
            AccountTransfer t = transfers.findById(transferId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "transfer.unknown")));
            if (t.status() == TransferStatus.REVERSED) {
                throw ValidationException.of(FieldError.of(null, "transfer.alreadyReversed"));
            }
            // l'entrée d'abord : c'est elle qui retire l'argent du compte destination
            reverseLeg(t.inTransactionId(), reason);
            reverseLeg(t.outTransactionId(), reason);
            if (t.feeTransactionId() != null) {
                reverseLeg(t.feeTransactionId(), reason);
            }
            AccountTransfer reversed = t.reversed(reason);
            long version = transfers.update(reversed);
            audit.event(AUDIT_TYPE, t.id(), AuditAction.REVERSE, Map.of("amount", t.amount()), reason);
            return reversed.withIdentity(t.id(), version);
        });
    }

    private void reverseLeg(Long txId, String reason) {
        if (txId != null) {
            ledger.reverse(txId, reason, ledger.today());
        }
    }

    private FinancialTransaction leg(long transferId, AccountTransfer t, TransactionDirection direction,
                                     SystemCategory category, Long accountId, Money amount,
                                     String label) {
        return new FinancialTransaction(null, 0, t.date(), null, direction, categories.system(category).id(), null,
                accountId, amount, null, null, label, t.reference(), TransactionSource.TRANSFER, transferId, null,
                null, null);
    }
}
