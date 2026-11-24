package org.gu.fms.desktop.features.loan;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.loan.Loan;
import org.gu.fms.domain.loan.LoanFrequency;
import org.gu.fms.domain.loan.LoanStatus;

import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Emprunts ; les véhicules financés (table loan_vehicle) sont lus avec l'emprunt. */
@Singleton
public final class LoanRepository extends JdbcRepository<Loan> {

    private static final String SELECT = "SELECT l.*, (SELECT group_concat(v.vehicle_id) FROM loan_vehicle v"
            + " WHERE v.loan_id = l.id) AS vehicle_ids FROM loan l";

    private final AppSettings settings;

    @Inject
    public LoanRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "loan";
    }

    @Override
    protected RowMapper<Loan> mapper() {
        return row -> {
            String ids = row.string("vehicle_ids");
            List<Long> vehicles = ids == null || ids.isBlank() ? List.of()
                    : Arrays.stream(ids.split(",")).map(String::strip).map(Long::valueOf).sorted().toList();
            return new Loan(row.longValue("id"), row.longValue("version"), row.string("lender"),
                    row.string("reference"), row.string("purpose"), row.money("principal", settings.currency()),
                    row.percent("annual_rate_bp"), row.intOrNull("term_count"),
                    row.enumValue("frequency", LoanFrequency.class), row.localDate("first_due_date"),
                    row.money("insurance_per_instalment", settings.currency()),
                    row.money("bank_instalment", settings.currency()), row.localDate("disbursement_date"),
                    row.longOrNull("disbursement_account_id"), row.longOrNull("disbursement_transaction_id"),
                    row.enumValue("status", LoanStatus.class), row.string("cancel_reason"), row.string("notes"),
                    vehicles);
        };
    }

    @Override
    public Optional<Loan> findById(long id) {
        return jdbc.queryOne(SELECT + " WHERE l.id = ?", mapper(), id);
    }

    @Override
    public List<Loan> findAll() {
        return jdbc.query(SELECT + " ORDER BY l.status, l.first_due_date, l.id", mapper());
    }

    @Override
    protected Long idOf(Loan l) {
        return l.id();
    }

    @Override
    protected long versionOf(Loan l) {
        return l.version();
    }

    @Override
    protected Map<String, Object> columnsOf(Loan l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lender", l.lender());
        m.put("reference", l.reference());
        m.put("purpose", l.purpose());
        m.put("principal", l.principal() == null ? null : l.principal().minor());
        m.put("annual_rate_bp", l.annualRate() == null ? null : l.annualRate().basisPoints());
        m.put("term_count", l.termCount());
        m.put("frequency", l.frequency());
        m.put("first_due_date", l.firstDueDate());
        m.put("insurance_per_instalment", l.insurancePerInstalment() == null ? 0L : l.insurancePerInstalment().minor());
        m.put("bank_instalment", l.bankInstalment() == null ? null : l.bankInstalment().minor());
        m.put("disbursement_date", l.disbursementDate());
        m.put("disbursement_account_id", l.disbursementAccountId());
        m.put("disbursement_transaction_id", l.disbursementTransactionId());
        m.put("status", l.status());
        m.put("cancel_reason", l.cancelReason());
        m.put("notes", l.notes());
        return m;
    }

    @Override
    public Map<String, Object> snapshot(Loan l) {
        Map<String, Object> m = new LinkedHashMap<>(columnsOf(l));
        m.put("vehicles", l.vehicleIds().toString());
        return m;
    }

    public void replaceVehicles(long loanId, List<Long> vehicleIds) {
        jdbc.update("DELETE FROM loan_vehicle WHERE loan_id = ?", loanId);
        for (Long v : vehicleIds) {
            jdbc.update("INSERT INTO loan_vehicle (loan_id, vehicle_id) VALUES (?, ?)", loanId, v);
        }
    }
}
