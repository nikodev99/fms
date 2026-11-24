package org.gu.fms.domain.loan;

import org.gu.fms.domain.common.Percent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tableau d'amortissement à échéances constantes (instructions §7).
 *
 * <ul>
 *   <li>taux périodique = taux annuel nominal ÷ nombre de périodes par an (taux proportionnel) ;</li>
 *   <li>échéance hors assurance A = P·r / (1 − (1 + r)^−n), arrondie au franc (au plus proche) ; taux nul : A = P ÷ n ;</li>
 *   <li>intérêts de chaque période = capital restant dû × r, arrondis au franc ; capital = A − intérêts ;</li>
 *   <li>la dernière échéance solde le capital restant : elle absorbe les arrondis ;</li>
 *   <li>l'assurance emprunteur s'ajoute à chaque échéance (montant fixe par échéance) ;</li>
 *   <li>si la banque communique une échéance différente (assurance comprise), elle fait foi : le taux périodique réel
 *       (implicite) est celui qui donne exactement cette échéance, puis le tableau est construit avec ce taux.</li>
 * </ul>
 */
public final class LoanSchedule {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int RATE_SCALE = 20;

    private LoanSchedule() {
    }

    public record Line(int seq, LocalDate dueDate, long principal, long interest, long insurance, long total,
                       long balanceAfter) {
    }

    /**
     * @param annuity           échéance hors assurance
     * @param periodicRate      taux périodique utilisé (fraction : 0,01 = 1 %)
     * @param annualRatePercent taux annuel nominal correspondant, en % (implicite si l'échéance vient de la banque)
     * @param bankOverride      l'échéance indiquée par la banque a été retenue
     */
    public record Result(List<Line> lines, long annuity, BigDecimal periodicRate, BigDecimal annualRatePercent,
                         boolean bankOverride) {

        public long totalInterest() {
            return lines.stream().mapToLong(Line::interest).sum();
        }

        public long totalInsurance() {
            return lines.stream().mapToLong(Line::insurance).sum();
        }

        public long totalPaid() {
            return lines.stream().mapToLong(Line::total).sum();
        }
    }

    public static BigDecimal periodicRate(Percent annualRate, LoanFrequency frequency) {
        return annualRate.value().divide(BigDecimal.valueOf(100L * frequency.periodsPerYear()), RATE_SCALE,
                RoundingMode.HALF_EVEN);
    }

    /** Échéance constante hors assurance, arrondie au franc. */
    public static long annuity(long principal, BigDecimal rate, int count) {
        if (rate.signum() == 0) {
            return BigDecimal.valueOf(principal).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).longValueExact();
        }
        BigDecimal growth = BigDecimal.ONE.add(rate).pow(count, MC);
        BigDecimal a = BigDecimal.valueOf(principal).multiply(rate, MC).multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), MC);
        return a.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /**
     * Taux périodique implicite d'une échéance constante {@code annuity} (hors assurance) : valeur actuelle des
     * échéances = capital. Nul si annuity × n = capital.
     *
     * @throws IllegalArgumentException si les échéances ne remboursent même pas le capital
     */
    public static BigDecimal impliedRate(long principal, long annuity, int count) {
        long total = Math.multiplyExact(annuity, (long) count);
        if (total < principal) {
            throw new IllegalArgumentException("Échéances insuffisantes pour rembourser le capital");
        }
        if (total == principal) {
            return BigDecimal.ZERO;
        }
        double lo = 0;
        double hi = 0.01;
        while (presentValue(annuity, hi, count) > principal) {
            hi *= 2;
        }
        for (int i = 0; i < 300; i++) {
            double mid = (lo + hi) / 2;
            if (presentValue(annuity, mid, count) > principal) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return new BigDecimal((lo + hi) / 2, new MathContext(15)).setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
    }

    private static double presentValue(long annuity, double rate, int count) {
        return rate == 0 ? (double) annuity * count : annuity * (1 - Math.pow(1 + rate, -count)) / rate;
    }

    /**
     * Construit le tableau.
     *
     * @param bankInstalment échéance indiquée par la banque, assurance comprise ; null = calcul
     */
    public static Result build(long principal, Percent annualRate, int count, LoanFrequency frequency,
                               LocalDate firstDueDate, long insurance, Long bankInstalment) {
        BigDecimal rate;
        long annuity;
        boolean override = bankInstalment != null;
        if (override) {
            annuity = bankInstalment - insurance;
            rate = impliedRate(principal, annuity, count);
        } else {
            rate = periodicRate(annualRate, frequency);
            annuity = annuity(principal, rate, count);
        }
        List<Line> lines = new ArrayList<>(count);
        long balance = principal;
        for (int k = 1; k <= count; k++) {
            long interest = BigDecimal.valueOf(balance).multiply(rate, MC).setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            long capital = Math.max(0, annuity - interest);
            if (k == count || capital > balance) {
                capital = balance;
            }
            balance -= capital;
            LocalDate due = firstDueDate.plusMonths((long) (k - 1) * frequency.monthsPerPeriod());
            lines.add(new Line(k, due, capital, interest, insurance, capital + interest + insurance, balance));
        }
        BigDecimal annualPercent = rate.multiply(BigDecimal.valueOf(100L * frequency.periodsPerYear()), MC)
                .setScale(4, RoundingMode.HALF_UP);
        return new Result(List.copyOf(lines), annuity, rate, annualPercent, override);
    }
}
