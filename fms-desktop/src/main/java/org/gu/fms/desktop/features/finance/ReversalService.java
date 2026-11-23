package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionRules;
import org.gu.fms.domain.finance.TransactionSource;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Contre-passation demandée depuis l'écran Transactions (instructions §4.4). Une saisie directe est contre-passée
 * telle quelle ; une transaction de module passe par le gestionnaire du module. Versements et écarts de clôture
 * se corrigent depuis leur écran (avoir, réouverture), jamais ici.
 */
@Singleton
public final class ReversalService {

    private final TransactionRepository transactions;
    private final Ledger ledger;
    private final TransactionManager tx;
    private final Map<TransactionSource, ReversalHandler> handlers = new EnumMap<>(TransactionSource.class);

    @Inject
    public ReversalService(TransactionRepository transactions, Ledger ledger, TransactionManager tx,
                           Set<ReversalHandler> moduleHandlers) {
        this.transactions = transactions;
        this.ledger = ledger;
        this.tx = tx;
        for (ReversalHandler h : moduleHandlers) {
            h.sources().forEach(s -> handlers.put(s, h));
        }
    }

    /** Peut-on contre-passer cette transaction depuis l'écran Transactions ? Clé du motif de refus, ou null. */
    public String refusal(FinancialTransaction t) {
        if (t.isReversal()) {
            return "transaction.reversal.ofReversal";
        }
        if (t.isReversed()) {
            return "transaction.reversal.already";
        }
        if (t.source() == TransactionSource.COLLECTION) {
            return "transaction.reversal.collection";
        }
        if (t.source() == TransactionSource.CASH_CLOSING) {
            return "transaction.reversal.closing";
        }
        if (t.source() != TransactionSource.MANUAL && t.source() != TransactionSource.MAINTENANCE
                && !handlers.containsKey(t.source())) {
            return "transaction.reversal.unsupported";
        }
        return null;
    }

    public FinancialTransaction reverse(long transactionId, String reason) {
        String text = TextRules.normalize(reason);
        if (text == null) {
            throw ValidationException.of(FieldError.of("reason", "transaction.reversal.reasonRequired"));
        }
        if (text.length() > TransactionRules.REASON_MAX) {
            throw ValidationException.of(FieldError.of("reason", "validation.maxLength", TransactionRules.REASON_MAX));
        }
        return tx.inTransaction(c -> {
            FinancialTransaction t = transactions.findById(transactionId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "transaction.unknown")));
            String refusal = refusal(t);
            if (refusal != null) {
                throw ValidationException.of(FieldError.of(null, refusal));
            }
            ReversalHandler handler = handlers.get(t.source());
            return handler == null ? ledger.reverse(t.id(), text, ledger.today()) : handler.reverse(t, text);
        });
    }
}
