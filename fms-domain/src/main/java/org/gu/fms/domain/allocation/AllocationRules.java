package org.gu.fms.domain.allocation;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AllocationRules {

    public static final int NAME_MAX = 60;
    public static final int METHOD_MAX = 500;

    private AllocationRules() {
    }

    /** Efface la valeur qui ne correspond pas à la base choisie (taux OU montant). */
    public static AllocationRule normalize(AllocationRule r) {
        boolean pct = r.basis() != null && r.basis().isPercentage();
        return r.normalized(TextRules.normalizeSpaces(r.name()), pct ? r.rate() : null, pct ? null : r.amount(),
                TextRules.normalize(r.method()));
    }

    public static List<FieldError> validate(AllocationRule r) {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "name", r.name(), NAME_MAX);
        TextRules.required(errors, "purpose", r.purpose());
        TextRules.required(errors, "basis", r.basis());
        if (r.basis() != null) {
            if (r.basis().isPercentage()) {
                TextRules.required(errors, "rate", r.rate());
                if (r.rate() != null && r.rate().isZero()) {
                    errors.add(FieldError.of("rate", "validation.positive"));
                }
            } else {
                TextRules.required(errors, "amount", r.amount());
                TextRules.positive(errors, "amount", r.amount());
            }
        }
        TextRules.requireText(errors, "method", r.method(), METHOD_MAX);
        return errors;
    }

    /**
     * Total des provisions actives calculées sur la MÊME base en pourcentage que {@code candidate},
     * candidate comprise (elle remplace sa version enregistrée dans {@code all}).
     * Au-delà de 100 %, on mettrait de côté plus que ce qui est encaissé : le service refuse.
     */
    public static BigDecimal totalForBasis(AllocationRule candidate, List<AllocationRule> all) {
        BigDecimal sum = BigDecimal.ZERO;
        List<AllocationRule> merged = new ArrayList<>();
        for (AllocationRule r : all) {
            if (!Objects.equals(r.id(), candidate.id())) {
                merged.add(r);
            }
        }
        merged.add(candidate);
        for (AllocationRule r : merged) {
            if (r.active() && r.basis() == candidate.basis() && r.rate() != null) {
                sum = sum.add(r.rate().value());
            }
        }
        return sum;
    }

    public static boolean exceedsHundred(BigDecimal total) {
        return total.compareTo(BigDecimal.valueOf(100)) > 0;
    }
}
