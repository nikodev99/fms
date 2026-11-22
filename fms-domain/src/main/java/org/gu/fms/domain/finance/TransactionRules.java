package org.gu.fms.domain.finance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Règles du grand livre de trésorerie, partagées Desktop / SaaS. */
public final class TransactionRules {

    public static final int LABEL_MAX = 200;
    public static final int REFERENCE_MAX = 60;
    public static final int REASON_MAX = 300;

    private TransactionRules() {
    }

    public static FinancialTransaction normalize(FinancialTransaction t) {
        return t.withText(TextRules.normalizeSpaces(t.label()), TextRules.normalizeSpaces(t.reference()));
    }

    /**
     * Valide une transaction normalisée et sa catégorie (null si inconnue : signalé ici).
     * Une transaction ne peut pas être datée dans le futur ; la date de clôture est contrôlée par le service.
     */
    public static List<FieldError> validate(FinancialTransaction t, TransactionCategory category, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.required(errors, "date", t.date());
        if (t.date() != null && t.date().isAfter(today)) {
            errors.add(FieldError.of("date", "validation.notInFuture"));
        }
        TextRules.required(errors, "direction", t.direction());
        TextRules.required(errors, "accountId", t.accountId());
        TextRules.required(errors, "amount", t.amount());
        TextRules.positive(errors, "amount", t.amount());
        TextRules.maxLength(errors, "label", t.label(), LABEL_MAX);
        TextRules.maxLength(errors, "reference", t.reference(), REFERENCE_MAX);
        if (t.categoryId() == null) {
            errors.add(FieldError.of("categoryId", "validation.required"));
        } else if (category == null) {
            errors.add(FieldError.of("categoryId", "transaction.category.unknown"));
        } else {
            if (!t.isReversal() && t.direction() != null && category.direction() != t.direction()) {
                errors.add(FieldError.of("categoryId", "transaction.category.wrongDirection"));
            }
            if (category.requiresVehicle() && t.vehicleId() == null && !t.isReversal()) {
                errors.add(FieldError.of("vehicleId", "transaction.vehicle.required"));
            }
        }
        return errors;
    }

    /** Contre-passation : même compte, catégorie et montant, sens opposé, datée de {@code today}. */
    public static FinancialTransaction reversalOf(FinancialTransaction original, LocalDate today, Instant now,
                                                  String user, String reason) {
        if (original.isReversal()) {
            throw new IllegalArgumentException("Une contre-passation ne se contre-passe pas");
        }
        if (original.isReversed()) {
            throw new IllegalArgumentException("Transaction déjà contre-passée : " + original.id());
        }
        String label = reason == null ? "Annulation" : "Annulation : " + reason;
        if (label.length() > LABEL_MAX) {
            label = label.substring(0, LABEL_MAX);
        }
        return new FinancialTransaction(null, 0, today, now, original.direction().opposite(), original.categoryId(),
                original.nature(), original.accountId(), original.amount(), original.vehicleId(), original.driverId(),
                label, original.reference(), original.source(), original.sourceId(), original.id(), null, user);
    }
}
