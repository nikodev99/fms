package org.gu.fms.domain.allocation;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;

/**
 * Règle de provision (instructions §4.6), commune à toute la flotte.
 * <p>
 * Une provision est une somme MISE DE CÔTÉ pour une dépense future : ce n'est jamais une dépense réelle.
 * Le calcul des montants alloués (et leur comparaison aux dépenses réelles) appartient aux modules
 * Versements et Rapports ; cette règle ne fait que décrire la méthode.
 *
 * @param rate   pour une base en pourcentage
 * @param amount pour une base en montant (fixe ou par km)
 * @param method description de la méthode, affichée dans les rapports (obligatoire : chaque provision documente son calcul)
 */
public record AllocationRule(
        Long id,
        long version,
        String name,
        AllocationPurpose purpose,
        AllocationBasis basis,
        Percent rate,
        Money amount,
        boolean active,
        String method) {

    public boolean isNew() {
        return id == null;
    }

    public AllocationRule withIdentity(long newId, long newVersion) {
        return new AllocationRule(newId, newVersion, name, purpose, basis, rate, amount, active, method);
    }

    AllocationRule normalized(String nName, Percent nRate, Money nAmount, String nMethod) {
        return new AllocationRule(id, version, nName, purpose, basis, nRate, nAmount, active, nMethod);
    }
}
