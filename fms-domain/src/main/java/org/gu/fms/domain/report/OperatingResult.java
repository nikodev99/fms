package org.gu.fms.domain.report;

import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.finance.TransactionNature;
import org.gu.fms.domain.payroll.Payslip;
import org.gu.fms.domain.payroll.PayslipStatus;

import java.util.Collection;
import java.util.Map;

/**
 * Résultat d'exploitation d'une période (instructions §18) — méthode :
 * <ul>
 *   <li><b>recettes d'exploitation</b> = transactions des catégories d'entrée de nature « exploitation » (versements,
 *       locations, excédents de caisse, autres recettes), contre-passations déduites ;</li>
 *   <li><b>+ manquants récupérés sur salaire</b> (bulletins validés ou payés de la période) : versements dus et
 *       finalement encaissés par retenue ;</li>
 *   <li><b>charges d'exploitation</b> = transactions des catégories de sortie de nature « exploitation » (carburant,
 *       entretien, assurance, frais…) contre-passations déduites, <b>+ coût employeur</b> des bulletins validés ou
 *       payés de la période (salaire brut et charges patronales) ;</li>
 *   <li>exclus : virements internes, financement (emprunts, apports, prélèvements), investissement (achat et vente
 *       de véhicules), charges financières (intérêts, assurance emprunteur), mouvements des comptes chauffeurs
 *       (avances), et les sorties de paie de la trésorerie (déjà comptées par le coût employeur).</li>
 * </ul>
 */
public record OperatingResult(long revenue, long shortageRecovered, long operatingExpenses, long payrollCost) {

    public static final OperatingResult ZERO = new OperatingResult(0, 0, 0, 0);

    /** Charges d'exploitation, salaires du personnel compris. */
    public long charges() {
        return operatingExpenses + payrollCost;
    }

    public long totalRevenue() {
        return revenue + shortageRecovered;
    }

    public long result() {
        return totalRevenue() - charges();
    }

    public OperatingResult plus(OperatingResult o) {
        return new OperatingResult(revenue + o.revenue, shortageRecovered + o.shortageRecovered,
                operatingExpenses + o.operatingExpenses, payrollCost + o.payrollCost);
    }

    /**
     * @param transactions      transactions de la période (contre-passations comprises)
     * @param categoryDirection sens de chaque catégorie (identifiant → sens)
     * @param payslips          bulletins des mois de la période (tous états ; seuls validés et payés comptent)
     */
    public static OperatingResult of(Collection<FinancialTransaction> transactions,
                                     Map<Long, TransactionDirection> categoryDirection,
                                     Collection<Payslip> payslips) {
        long revenue = 0;
        long expenses = 0;
        for (FinancialTransaction t : transactions) {
            if (t.nature() != TransactionNature.OPERATING) {
                continue;
            }
            TransactionDirection categoryWay = categoryDirection.getOrDefault(t.categoryId(), t.direction());
            if (categoryWay == TransactionDirection.INFLOW) {
                revenue += t.signedMinor();
            } else {
                expenses -= t.signedMinor();
            }
        }
        long payroll = 0;
        long recovered = 0;
        for (Payslip p : payslips) {
            if (p.status() == PayslipStatus.VALIDATED || p.status() == PayslipStatus.PAID) {
                payroll += p.employerCost() == null ? 0 : p.employerCost().minor();
                recovered += p.shortageRecovered() == null ? 0 : p.shortageRecovered().minor();
            }
        }
        return new OperatingResult(revenue, recovered, expenses, payroll);
    }
}
