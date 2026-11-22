package org.gu.fms.domain.finance;

/**
 * Nature financière d'une catégorie : décide où une transaction compte dans les rapports (instructions §4.4, §18).
 * Toutes les natures font varier les soldes de trésorerie ; seules certaines entrent dans le résultat.
 *
 * <ul>
 *   <li>{@link #OPERATING} : recettes et charges d'exploitation (versements, carburant, entretien…) → résultat ;</li>
 *   <li>{@link #FINANCIAL_CHARGE} : intérêts d'emprunt → déduits après le résultat d'exploitation ;</li>
 *   <li>{@link #PAYROLL} : salaires nets versés, charges sociales payées → HORS résultat : le coût de la paie
 *       entre dans le résultat par les bulletins (brut + charges patronales), pas par les paiements ;</li>
 *   <li>{@link #DRIVER_ACCOUNT} : avances aux chauffeurs et leurs remboursements → créances, hors résultat ;</li>
 *   <li>{@link #FINANCING} : capital emprunté ou remboursé, apports et prélèvements du propriétaire → hors résultat ;</li>
 *   <li>{@link #INVESTMENT} : achat ou vente de véhicules → hors résultat ;</li>
 *   <li>{@link #TRANSFER} : virement entre deux comptes → neutre pour la trésorerie consolidée.</li>
 * </ul>
 */
public enum TransactionNature {
    OPERATING,
    FINANCIAL_CHARGE,
    PAYROLL,
    DRIVER_ACCOUNT,
    FINANCING,
    INVESTMENT,
    TRANSFER;

    /** Compte dans le résultat net d'exploitation (§18). */
    public boolean inOperatingResult() {
        return this == OPERATING;
    }
}
