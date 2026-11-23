package org.gu.fms.domain.collection;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcul du point journalier, identique à l'édition Excel :
 * attendu = somme des montants attendus des affectations actives ce jour-là (0 si exempté) ;
 * reçu = versements − avoirs datés de ce jour ; écart = reçu − attendu.
 */
public final class DailyPointRules {

    public static final int REASON_MAX = 300;

    private DailyPointRules() {
    }

    public static DailyPoint compute(LocalDate date, Long driverId, Long vehicleId, Money plannedExpected,
                                     Money received, DaySituation situation, String reason, String user, Instant now) {
        Money expected = situation == DaySituation.EXEMPT ? Money.zero(plannedExpected.currency()) : plannedExpected;
        return new DailyPoint(null, 0, date, driverId, vehicleId, expected, received, received.minus(expected),
                situation, TextRules.normalize(reason), user, now);
    }

    /** Un motif est exigé pour exempter un chauffeur. */
    public static List<FieldError> validate(DaySituation situation, String reason) {
        List<FieldError> errors = new ArrayList<>();
        String text = TextRules.normalize(reason);
        if (situation == DaySituation.EXEMPT && text == null) {
            errors.add(FieldError.of("reason", "dailyPoint.exemptReasonRequired"));
        }
        TextRules.maxLength(errors, "reason", text, REASON_MAX);
        return errors;
    }

    /**
     * Mouvement au compte chauffeur (rubrique Manquants) : + manquant, − excédent ; zéro = aucun mouvement.
     */
    public static long shortageMovement(DailyPoint point) {
        return -point.difference().minor();
    }
}
