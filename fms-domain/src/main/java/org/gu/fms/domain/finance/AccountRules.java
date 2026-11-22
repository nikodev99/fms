package org.gu.fms.domain.finance;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Règles des comptes de trésorerie, partagées Desktop / SaaS. */
public final class AccountRules {

    public static final int NAME_MAX = 60;
    public static final int NOTES_MAX = 500;

    private AccountRules() {
    }

    public static FinancialAccount normalize(FinancialAccount a) {
        return a.withText(TextRules.normalizeSpaces(a.name()), TextRules.normalize(a.notes()));
    }

    public static List<FieldError> validate(FinancialAccount a, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "name", a.name(), NAME_MAX);
        TextRules.required(errors, "openingBalance", a.openingBalance());
        TextRules.required(errors, "openingDate", a.openingDate());
        if (a.openingDate() != null && a.openingDate().isAfter(today)) {
            errors.add(FieldError.of("openingDate", "validation.notInFuture"));
        }
        if (a.openingBalance() != null && a.openingBalance().isNegative() && !a.overdraftAllowed()) {
            errors.add(FieldError.of("openingBalance", "account.negativeNotAllowed"));
        }
        if (a.overdraftAllowed() && a.type() != AccountType.BANK && a.type() != AccountType.OTHER) {
            errors.add(FieldError.of("overdraftAllowed", "account.overdraftOnlyBank"));
        }
        TextRules.maxLength(errors, "notes", a.notes(), NOTES_MAX);
        return errors;
    }

    /**
     * Changements interdits une fois que le compte a des mouvements (transactions ou clôtures) : solde et date
     * d'ouverture, type de compte.
     */
    public static List<FieldError> validateChange(FinancialAccount before, FinancialAccount after, boolean hasMovements) {
        List<FieldError> errors = new ArrayList<>();
        if (!hasMovements) {
            return errors;
        }
        if (!Objects.equals(before.openingBalance(), after.openingBalance())) {
            errors.add(FieldError.of("openingBalance", "account.openingLocked"));
        }
        if (!Objects.equals(before.openingDate(), after.openingDate())) {
            errors.add(FieldError.of("openingDate", "account.openingLocked"));
        }
        if (before.type() != after.type()) {
            errors.add(FieldError.of("type", "account.typeLocked"));
        }
        return errors;
    }

    /** Le solde après mouvement est-il acceptable pour ce compte ? */
    public static boolean balanceAllowed(FinancialAccount account, Money balanceAfter) {
        return account.overdraftAllowed() || !balanceAfter.isNegative();
    }
}
