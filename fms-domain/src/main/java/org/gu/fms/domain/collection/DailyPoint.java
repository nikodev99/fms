package org.gu.fms.domain.collection;

import org.gu.fms.domain.money.Money;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Point journalier d'un chauffeur (instructions §4.3) : FIGE le montant attendu et les sommes reçues du jour.
 * L'écart est porté au compte du chauffeur (manquant = dette ; excédent = compense les manquants).
 * Un versement ou un avoir daté d'un jour déjà pointé donne une RÉGULARISATION au compte chauffeur, le point
 * lui-même ne change plus. Le mouvement du compte chauffeur référence le point (source DAILY_POINT).
 *
 * @param expected   montant attendu retenu (0 si exempté)
 * @param difference reçu − attendu (négatif = manquant)
 */
public record DailyPoint(
        Long id,
        long version,
        LocalDate date,
        Long driverId,
        Long vehicleId,
        Money expected,
        Money received,
        Money difference,
        DaySituation situation,
        String reason,
        String validatedBy,
        Instant validatedAt) {

    public boolean isNew() {
        return id == null;
    }

    public DailyPoint withIdentity(long newId, long newVersion) {
        return new DailyPoint(newId, newVersion, date, driverId, vehicleId, expected, received, difference, situation,
                reason, validatedBy, validatedAt);
    }
}
