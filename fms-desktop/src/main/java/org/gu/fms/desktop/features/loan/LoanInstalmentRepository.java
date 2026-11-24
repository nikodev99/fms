package org.gu.fms.desktop.features.loan;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.loan.InstalmentStatus;
import org.gu.fms.domain.loan.LoanInstalment;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class LoanInstalmentRepository extends JdbcRepository<LoanInstalment> {

    private final AppSettings settings;

    @Inject
    public LoanInstalmentRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "loan_instalment";
    }

    @Override
    protected String defaultOrder() {
        return "loan_id, seq";
    }

    @Override
    protected RowMapper<LoanInstalment> mapper() {
        return row -> new LoanInstalment(row.longValue("id"), row.longValue("version"), row.longValue("loan_id"),
                row.intValue("seq"), row.localDate("due_date"), row.money("principal", settings.currency()),
                row.money("interest", settings.currency()), row.money("insurance", settings.currency()),
                row.money("total", settings.currency()), row.money("balance_after", settings.currency()),
                row.enumValue("status", InstalmentStatus.class), row.localDate("paid_on"), row.longOrNull("account_id"),
                row.longOrNull("principal_tx_id"), row.longOrNull("interest_tx_id"), row.longOrNull("insurance_tx_id"));
    }

    @Override
    protected Long idOf(LoanInstalment i) {
        return i.id();
    }

    @Override
    protected long versionOf(LoanInstalment i) {
        return i.version();
    }

    @Override
    protected Map<String, Object> columnsOf(LoanInstalment i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loan_id", i.loanId());
        m.put("seq", i.seq());
        m.put("due_date", i.dueDate());
        m.put("principal", i.principal().minor());
        m.put("interest", i.interest().minor());
        m.put("insurance", i.insurance().minor());
        m.put("total", i.total().minor());
        m.put("balance_after", i.balanceAfter().minor());
        m.put("status", i.status());
        m.put("paid_on", i.paidOn());
        m.put("account_id", i.accountId());
        m.put("principal_tx_id", i.principalTxId());
        m.put("interest_tx_id", i.interestTxId());
        m.put("insurance_tx_id", i.insuranceTxId());
        return m;
    }

    public List<LoanInstalment> findByLoan(long loanId) {
        return jdbc.query("SELECT * FROM loan_instalment WHERE loan_id = ? ORDER BY seq", mapper(), loanId);
    }

    /** Échéances à payer des emprunts en cours, de la plus ancienne à la plus récente. */
    public List<LoanInstalment> findDueOfActiveLoans() {
        return jdbc.query("SELECT i.* FROM loan_instalment i JOIN loan l ON l.id = i.loan_id"
                + " WHERE i.status = 'DUE' AND l.status = 'ACTIVE' ORDER BY i.due_date, i.loan_id, i.seq", mapper());
    }

    public List<LoanInstalment> findDueBetween(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT i.* FROM loan_instalment i JOIN loan l ON l.id = i.loan_id WHERE i.status = 'DUE'"
                + " AND l.status = 'ACTIVE' AND i.due_date BETWEEN ? AND ? ORDER BY i.due_date", mapper(), from, to);
    }

    /** Supprime les échéances non payées (nouveau tableau, annulation). */
    public void deleteUnpaid(long loanId) {
        jdbc.update("DELETE FROM loan_instalment WHERE loan_id = ? AND status <> 'PAID'", loanId);
    }

    public void cancelUnpaid(long loanId) {
        jdbc.update("UPDATE loan_instalment SET status = 'CANCELLED', version = version + 1, updated_at = ?"
                + " WHERE loan_id = ? AND status = 'DUE'", clock.instant(), loanId);
    }

    public long countPaid(long loanId) {
        return jdbc.queryLong("SELECT COUNT(*) FROM loan_instalment WHERE loan_id = ? AND status = 'PAID'", loanId);
    }
}
