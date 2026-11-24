package org.gu.fms.domain.collection;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.Percent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Règles de recouvrement des versements (Paramètres › Versements et recouvrement).
 *
 * <ul>
 *   <li><b>Jours de repos de la flotte</b> : aucun versement n'est attendu ; un versement reçu ce jour-là compte
 *       quand même (il compense les manquants du chauffeur).</li>
 *   <li><b>Objectif</b> : taux de recouvrement visé (reçu ÷ attendu), 95 % par défaut.</li>
 *   <li><b>Seuil rouge</b> : en dessous de cette part du montant attendu, la journée est « non versée » (rouge) ;
 *       entre ce seuil et 100 % elle est « partielle » (orange). Exprimé en pourcentage du montant attendu, pour
 *       rester juste quand les montants attendus diffèrent d'un chauffeur à l'autre.</li>
 * </ul>
 */
public record CollectionPolicy(Set<DayOfWeek> restDays, Percent objective, Percent redThreshold) {

    public static final CollectionPolicy DEFAULT =
            new CollectionPolicy(EnumSet.of(DayOfWeek.SUNDAY), Percent.of("95"), Percent.of("50"));

    public CollectionPolicy {
        restDays = restDays == null || restDays.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(restDays));
    }

    public boolean isRestDay(LocalDate date) {
        return restDays.contains(date.getDayOfWeek());
    }

    public List<FieldError> validate() {
        List<FieldError> errors = new ArrayList<>();
        if (objective == null || objective.isZero()) {
            errors.add(FieldError.of("objective", "collectionPolicy.objective.range"));
        }
        if (redThreshold == null || redThreshold.compareTo(Percent.HUNDRED) >= 0) {
            errors.add(FieldError.of("redThreshold", "collectionPolicy.red.range"));
        } else if (objective != null && redThreshold.compareTo(objective) > 0) {
            errors.add(FieldError.of("redThreshold", "collectionPolicy.red.aboveObjective"));
        }
        if (restDays.size() == DayOfWeek.values().length) {
            errors.add(FieldError.of("restDays", "collectionPolicy.restDays.all"));
        }
        return errors;
    }

    /** Taux en points de base (reçu ÷ attendu × 10 000, arrondi au plus proche) ; null si rien n'était attendu. */
    public static Long rateBp(long received, long expected) {
        if (expected <= 0) {
            return null;
        }
        return Math.round(received * 10_000.0 / expected);
    }

    /** Le taux atteint-il l'objectif ? (rien d'attendu = atteint). */
    public boolean meetsObjective(long received, long expected) {
        Long rate = rateBp(received, expected);
        return rate == null || rate >= objective.basisPoints();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CollectionPolicy p && restDays.equals(p.restDays) && Objects.equals(objective, p.objective)
                && Objects.equals(redThreshold, p.redThreshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(restDays, objective, redThreshold);
    }
}
