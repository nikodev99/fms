package org.gu.fms.domain.loan;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * Emprunt (instructions §7). Un emprunt peut financer un véhicule, plusieurs, ou l'activité en général (agrandir la
 * flotte, trésorerie) : les véhicules financés sont facultatifs.
 *
 * @param annualRate             taux annuel nominal (0 % possible)
 * @param insurancePerInstalment assurance emprunteur payée avec chaque échéance (0 si aucune)
 * @param bankInstalment         montant de l'échéance indiqué par la banque, assurance comprise ; s'il diffère du
 *                               calcul, il fait foi et le taux réel (implicite) est recalculé ; null = calcul
 * @param disbursementAccountId  compte qui a reçu les fonds (null si la banque a payé directement le fournisseur)
 */
public record Loan(
        Long id,
        long version,
        String lender,
        String reference,
        String purpose,
        Money principal,
        Percent annualRate,
        Integer termCount,
        LoanFrequency frequency,
        LocalDate firstDueDate,
        Money insurancePerInstalment,
        Money bankInstalment,
        LocalDate disbursementDate,
        Long disbursementAccountId,
        Long disbursementTransactionId,
        LoanStatus status,
        String cancelReason,
        String notes,
        List<Long> vehicleIds) {

    public Loan {
        vehicleIds = vehicleIds == null ? List.of() : List.copyOf(vehicleIds);
    }

    public boolean isNew() {
        return id == null;
    }

    public Loan withIdentity(long newId, long newVersion) {
        return new Loan(newId, newVersion, lender, reference, purpose, principal, annualRate, termCount, frequency,
                firstDueDate, insurancePerInstalment, bankInstalment, disbursementDate, disbursementAccountId,
                disbursementTransactionId, status, cancelReason, notes, vehicleIds);
    }

    public Loan withDisbursementTransaction(Long transactionId) {
        return new Loan(id, version, lender, reference, purpose, principal, annualRate, termCount, frequency,
                firstDueDate, insurancePerInstalment, bankInstalment, disbursementDate, disbursementAccountId,
                transactionId, status, cancelReason, notes, vehicleIds);
    }

    public Loan withStatus(LoanStatus newStatus, String reason) {
        return new Loan(id, version, lender, reference, purpose, principal, annualRate, termCount, frequency,
                firstDueDate, insurancePerInstalment, bankInstalment, disbursementDate, disbursementAccountId,
                disbursementTransactionId, newStatus, reason, notes, vehicleIds);
    }

    /** Libellé court : « BGFI · 8 000 000 » est construit par l'interface ; ici prêteur et référence. */
    public String label() {
        return reference == null ? lender : lender + " · " + reference;
    }
}
