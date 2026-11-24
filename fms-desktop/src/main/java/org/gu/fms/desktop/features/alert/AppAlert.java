package org.gu.fms.desktop.features.alert;

import java.time.LocalDate;

/**
 * Alerte à traiter.
 *
 * @param route     module où l'on règle le problème (identifiant de route)
 * @param vehicleId véhicule concerné, pour l'action « Mettre hors service » ; null sinon
 * @param driverId  chauffeur concerné, pour l'action « Suspendre » ; null sinon
 * @param date      échéance concernée (expiration, échéance d'emprunt…) ; null si sans objet
 */
public record AppAlert(String key, Severity severity, Kind kind, String route, String title, String detail,
                       Long vehicleId, Long driverId, LocalDate date) {

    public enum Severity {
        /** Bloquant : un dialogue impose la mise à jour (ou la mise hors service). */
        CRITICAL,
        WARNING,
        INFO
    }

    public enum Kind {
        DOCUMENT,
        MAINTENANCE,
        LOAN,
        DAILY_POINT
    }

    public boolean critical() {
        return severity == Severity.CRITICAL;
    }
}
