package org.gu.fms.domain.loan;

/** Périodicité des remboursements. Le taux périodique est le taux annuel nominal divisé par le nombre de périodes. */
public enum LoanFrequency {
    MONTHLY(12),
    QUARTERLY(4),
    SEMIANNUAL(2),
    ANNUAL(1);

    private final int periodsPerYear;

    LoanFrequency(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    public int periodsPerYear() {
        return periodsPerYear;
    }

    public int monthsPerPeriod() {
        return 12 / periodsPerYear;
    }
}
