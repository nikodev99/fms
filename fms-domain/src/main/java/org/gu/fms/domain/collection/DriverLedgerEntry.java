package org.gu.fms.domain.collection;

import org.gu.fms.domain.money.Money;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Mouvement au compte d'un chauffeur. Insertion seule : une erreur se corrige par un mouvement inverse.
 *
 * @param amount + augmente ce que le chauffeur doit (manquant, avance versée, dette) ;
 *               − le diminue (excédent, retenue sur salaire, remboursement)
 */
public record DriverLedgerEntry(
        Long id,
        long version,
        LocalDate date,
        Instant recordedAt,
        Long driverId,
        LedgerRubric rubric,
        Money amount,
        LedgerSource source,
        Long sourceId,
        String label,
        String userName) {

    public boolean isNew() {
        return id == null;
    }

    public DriverLedgerEntry withIdentity(long newId, long newVersion) {
        return new DriverLedgerEntry(newId, newVersion, date, recordedAt, driverId, rubric, amount, source, sourceId,
                label, userName);
    }
}
