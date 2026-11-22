package org.gu.fms.domain.payroll;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;

/**
 * Cotisation ou taxe sur salaire configurable (instructions §4.7 et §19).
 *
 * Datée : pour changer un taux, on crée une nouvelle ligne avec le même code et une date d'effet plus récente ;
 * la paie d'une période utilisera la ligne du code dont la date d'effet est la plus récente sans dépasser la période.
 * Les lignes passées restent donc la preuve des taux appliqués.
 *
 * @param monthlyCeiling plafond mensuel de l'assiette (null = sans plafond)
 * @param monthlyFloor   la cotisation ne porte que sur la part du salaire au-delà de ce montant (null = dès le premier franc)
 * @param enabled        appliquée en paie (Oui / Non) — décision du propriétaire, quel que soit le taux légal
 * @param legalSource    texte ou source d'où vient le taux (obligatoire : pas de taux sans origine)
 * @param verified       vrai seulement si le taux a été contrôlé sur un texte officiel congolais
 */
public record PayrollContribution(
        Long id,
        long version,
        String code,
        String name,
        ContributionGroup group,
        ContributionPayer payer,
        Percent rate,
        Money monthlyCeiling,
        Money monthlyFloor,
        boolean enabled,
        LocalDate effectiveFrom,
        String legalSource,
        boolean verified) {

    public boolean isNew() {
        return id == null;
    }

    public PayrollContribution withIdentity(long newId, long newVersion) {
        return new PayrollContribution(newId, newVersion, code, name, group, payer, rate, monthlyCeiling,
                monthlyFloor, enabled, effectiveFrom, legalSource, verified);
    }

    PayrollContribution withText(String nCode, String nName, String nSource) {
        return new PayrollContribution(id, version, nCode, nName, group, payer, rate, monthlyCeiling, monthlyFloor,
                enabled, effectiveFrom, nSource, verified);
    }
}
