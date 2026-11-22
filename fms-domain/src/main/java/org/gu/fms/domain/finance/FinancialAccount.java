package org.gu.fms.domain.finance;

import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Compte de trésorerie : caisse, MTN MoMo, Airtel Money, banque… (instructions §4.5).
 * <p>
 * Le solde n'est JAMAIS stocké : il est recalculé à partir du solde d'ouverture et des transactions du compte.
 * Solde d'ouverture et date d'ouverture se figent dès la première transaction : les changer ensuite réécrirait
 * l'historique de tous les soldes et clôtures.
 *
 * @param openingBalance         solde réel du compte à la date d'ouverture (avant toute transaction FMS)
 * @param includedInConsolidated compté dans la trésorerie consolidée
 * @param overdraftAllowed       le solde peut-il devenir négatif ? (découvert bancaire autorisé). Une caisse ou un
 *                               portefeuille Mobile Money ne peut pas descendre sous zéro.
 */
public record FinancialAccount(
        Long id,
        long version,
        String name,
        AccountType type,
        Money openingBalance,
        LocalDate openingDate,
        boolean includedInConsolidated,
        boolean overdraftAllowed,
        boolean active,
        String notes) {

    public FinancialAccount {
        Objects.requireNonNull(type, "type");
    }

    public boolean isNew() {
        return id == null;
    }

    public FinancialAccount withIdentity(long newId, long newVersion) {
        return new FinancialAccount(newId, newVersion, name, type, openingBalance, openingDate, includedInConsolidated,
                overdraftAllowed, active, notes);
    }

    FinancialAccount withText(String nName, String nNotes) {
        return new FinancialAccount(id, version, nName, type, openingBalance, openingDate, includedInConsolidated,
                overdraftAllowed, active, nNotes);
    }
}
