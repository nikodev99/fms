package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.Percent;

/**
 * Politique de retenues proposée à la paie (paramètres Paie).
 * <p>
 * Code du travail congolais (loi n° 45-75, art. 100) : les acomptes sur un travail en cours ne sont pas des
 * avances ; le remboursement d'avances d'argent et toute compensation au profit de l'employeur passent, sauf
 * décision judiciaire, par une cession volontaire souscrite devant le magistrat ou l'inspecteur du travail.
 * Les taux de retenue par paie sont fixés par décret (art. 101) — non trouvés, donc non codés.
 * Le logiciel PROPOSE ; le propriétaire valide et peut diminuer chaque retenue.
 *
 * @param recoverAdvances  proposer la retenue des avances / acomptes versés
 * @param recoverShortages proposer la retenue des manquants de versement
 * @param recoverOtherDebts proposer la retenue des autres dettes
 * @param debtCap          plafond des retenues de manquants + autres dettes, en % du net avant retenues
 */
public record RecoveryPolicy(boolean recoverAdvances, boolean recoverShortages, boolean recoverOtherDebts, Percent debtCap) {

    /** Comportement de l'édition Excel : tout est proposé, dans la limite du net. */
    public static RecoveryPolicy excelDefault() {
        return new RecoveryPolicy(true, true, true, Percent.of("100"));
    }
}
