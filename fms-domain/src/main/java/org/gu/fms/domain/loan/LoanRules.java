package org.gu.fms.domain.loan;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Règles d'un emprunt. */
public final class LoanRules {

    public static final int LENDER_MAX = 80;
    public static final int REFERENCE_MAX = 60;
    public static final int PURPOSE_MAX = 200;
    public static final int NOTES_MAX = 1000;
    public static final int TERM_MAX = 600;

    private LoanRules() {
    }

    public static Loan normalize(Loan l) {
        return new Loan(l.id(), l.version(), TextRules.normalizeSpaces(l.lender()),
                TextRules.normalizeSpaces(l.reference()), TextRules.normalize(l.purpose()), l.principal(),
                l.annualRate(), l.termCount(), l.frequency(), l.firstDueDate(), l.insurancePerInstalment(),
                l.bankInstalment(), l.disbursementAccountId() == null ? null : l.disbursementDate(),
                l.disbursementDate() == null ? null : l.disbursementAccountId(), l.disbursementTransactionId(),
                l.status() == null ? LoanStatus.ACTIVE : l.status(), TextRules.normalize(l.cancelReason()),
                TextRules.normalize(l.notes()), l.vehicleIds().stream().filter(Objects::nonNull).distinct().toList());
    }

    public static List<FieldError> validate(Loan l) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "lender", l.lender(), LENDER_MAX);
        TextRules.maxLength(errors, "reference", l.reference(), REFERENCE_MAX);
        TextRules.maxLength(errors, "purpose", l.purpose(), PURPOSE_MAX);
        TextRules.maxLength(errors, "notes", l.notes(), NOTES_MAX);
        TextRules.required(errors, "principal", l.principal());
        TextRules.positive(errors, "principal", l.principal());
        TextRules.required(errors, "annualRate", l.annualRate());
        TextRules.required(errors, "termCount", l.termCount());
        if (l.termCount() != null && (l.termCount() < 1 || l.termCount() > TERM_MAX)) {
            errors.add(FieldError.of("termCount", "validation.range", 1, TERM_MAX));
        }
        TextRules.required(errors, "frequency", l.frequency());
        TextRules.required(errors, "firstDueDate", l.firstDueDate());
        TextRules.notNegative(errors, "insurancePerInstalment", l.insurancePerInstalment());
        if (l.bankInstalment() != null && l.principal() != null && l.termCount() != null && l.termCount() >= 1) {
            long insurance = l.insurancePerInstalment() == null ? 0 : l.insurancePerInstalment().minor();
            long annuity = l.bankInstalment().minor() - insurance;
            if (annuity <= 0) {
                errors.add(FieldError.of("bankInstalment", "loan.bankInstalment.belowInsurance"));
            } else if (Math.multiplyExact(annuity, (long) l.termCount()) < l.principal().minor()) {
                errors.add(FieldError.of("bankInstalment", "loan.bankInstalment.tooLow"));
            }
        }
        LocalDate disbursed = l.disbursementDate();
        if (disbursed != null && l.firstDueDate() != null && disbursed.isAfter(l.firstDueDate())) {
            errors.add(FieldError.of("disbursementDate", "loan.disbursementAfterFirstDue"));
        }
        return errors;
    }

    /** Tableau d'amortissement d'un emprunt valide. */
    public static LoanSchedule.Result schedule(Loan l) {
        long insurance = l.insurancePerInstalment() == null ? 0 : l.insurancePerInstalment().minor();
        return LoanSchedule.build(l.principal().minor(), l.annualRate(), l.termCount(), l.frequency(),
                l.firstDueDate(), insurance, l.bankInstalment() == null ? null : l.bankInstalment().minor());
    }

    /** Les conditions financières (et donc le tableau) changent-elles ? */
    public static boolean termsChanged(Loan before, Loan after) {
        return !Objects.equals(before.principal(), after.principal())
                || !Objects.equals(before.annualRate(), after.annualRate())
                || !Objects.equals(before.termCount(), after.termCount())
                || before.frequency() != after.frequency()
                || !Objects.equals(before.firstDueDate(), after.firstDueDate())
                || !Objects.equals(before.insurancePerInstalment(), after.insurancePerInstalment())
                || !Objects.equals(before.bankInstalment(), after.bankInstalment());
    }

    /** Le déblocage (compte, date) change-t-il ? */
    public static boolean disbursementChanged(Loan before, Loan after) {
        return !Objects.equals(before.disbursementAccountId(), after.disbursementAccountId())
                || !Objects.equals(before.disbursementDate(), after.disbursementDate());
    }
}
