package org.gu.fms.domain.payroll;

import org.gu.fms.domain.driver.PayScheme;
import org.gu.fms.domain.money.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Bulletin de paie mensuel d'un chauffeur (instructions §4.7).
 *
 * Le calcul et le paiement sont des enregistrements distincts : le bulletin porte le calcul ; le paiement est une
 * sortie de trésorerie liée ({@code transactionId}), enregistrée quand l'argent a réellement été versé (paiement
 * manuel hors application : espèces, virement, application Mobile Money).
 */
public record Payslip(
        Long id,
        long version,
        YearMonth period,
        Long driverId,
        PayScheme payScheme,
        Money collectionsBase,
        Money fixedPart,
        Money commission,
        Money adjustment,
        String adjustmentReason,
        Money gross,
        Money employeeContributions,
        Money its,
        BigDecimal itsParts,
        Money netBeforeRecoveries,
        Money advanceRecovered,
        Money shortageRecovered,
        Money otherDebtRecovered,
        String recoveryNote,
        Money netPayable,
        Money employerCharges,
        Money employerCost,
        PayslipStatus status,
        String validatedBy,
        Instant validatedAt,
        LocalDate paidDate,
        Long accountId,
        String paymentReference,
        Long transactionId,
        String cancelReason,
        List<PayslipLine> lines) {

    public Payslip {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public boolean isNew() {
        return id == null;
    }

    public Recoveries recoveries() {
        return new Recoveries(advanceRecovered, shortageRecovered, otherDebtRecovered);
    }

    /** Bulletin calculé (brouillon) à partir d'un résultat de calcul. */
    public static Payslip draft(Long id, long version, YearMonth period, Long driverId, PayScheme scheme,
                                Money collectionsBase, Money adjustment, String adjustmentReason, String recoveryNote,
                                PayrollResult r) {
        return new Payslip(id, version, period, driverId, scheme, collectionsBase, r.fixedPart(), r.commission(),
                adjustment, adjustmentReason, r.gross(), r.employeeContributions(), r.its(), r.itsParts(),
                r.netBeforeRecoveries(), r.recoveries().advance(), r.recoveries().shortage(),
                r.recoveries().otherDebt(), recoveryNote, r.netPayable(), r.employerCharges(), r.employerCost(),
                PayslipStatus.DRAFT, null, null, null, null, null, null, null, r.lines());
    }

    public Payslip withIdentity(long newId, long newVersion) {
        return with(newId, newVersion, status, validatedBy, validatedAt, paidDate, accountId, paymentReference,
                transactionId, cancelReason);
    }

    public Payslip validated(String user, Instant at) {
        return with(id, version, PayslipStatus.VALIDATED, user, at, null, null, null, null, null);
    }

    /** Paiement enregistré ; {@code txId} null quand le net à payer est nul (rien à verser). */
    public Payslip paid(LocalDate date, Long account, String reference, Long txId) {
        return with(id, version, PayslipStatus.PAID, validatedBy, validatedAt, date, account, reference, txId, null);
    }

    /** Paiement contre-passé : retour à l'état validé. */
    public Payslip unpaid() {
        return with(id, version, PayslipStatus.VALIDATED, validatedBy, validatedAt, null, null, null, null, null);
    }

    public Payslip cancelled(String reason) {
        return with(id, version, PayslipStatus.CANCELLED, validatedBy, validatedAt, null, null, null, null, reason);
    }

    private Payslip with(Long nId, long nVersion, PayslipStatus nStatus, String nBy, Instant nAt, LocalDate nPaid,
                         Long nAccount, String nRef, Long nTx, String nCancel) {
        return new Payslip(nId, nVersion, period, driverId, payScheme, collectionsBase, fixedPart, commission,
                adjustment, adjustmentReason, gross, employeeContributions, its, itsParts, netBeforeRecoveries,
                advanceRecovered, shortageRecovered, otherDebtRecovered, recoveryNote, netPayable, employerCharges,
                employerCost, nStatus, nBy, nAt, nPaid, nAccount, nRef, nTx, nCancel, lines);
    }
}
