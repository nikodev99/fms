package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.finance.CashClosing;
import org.gu.fms.domain.finance.ClosingStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public final class ClosingRepository extends JdbcRepository<CashClosing> {

    private final AppSettings settings;

    @Inject
    public ClosingRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "cash_closing";
    }

    @Override
    protected String defaultOrder() {
        return "closing_date DESC, id DESC";
    }

    @Override
    protected RowMapper<CashClosing> mapper() {
        var c = settings.currency();
        return row -> new CashClosing(row.longValue("id"), row.longValue("version"), row.longValue("account_id"),
                row.localDate("closing_date"), row.money("opening_balance", c), row.money("inflows", c),
                row.money("outflows", c), row.money("transfers_in", c), row.money("transfers_out", c),
                row.money("expected", c), row.money("actual", c), row.money("difference", c), row.string("explanation"),
                row.longOrNull("difference_transaction_id"), row.string("cashier"), row.instant("closed_at"),
                row.enumValue("status", ClosingStatus.class), row.string("reopen_reason"), row.instant("reopened_at"));
    }

    @Override
    protected Long idOf(CashClosing c) {
        return c.id();
    }

    @Override
    protected long versionOf(CashClosing c) {
        return c.version();
    }

    @Override
    protected Map<String, Object> columnsOf(CashClosing c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("account_id", c.accountId());
        m.put("closing_date", c.date());
        m.put("opening_balance", c.openingBalance());
        m.put("inflows", c.inflows());
        m.put("outflows", c.outflows());
        m.put("transfers_in", c.transfersIn());
        m.put("transfers_out", c.transfersOut());
        m.put("expected", c.expected());
        m.put("actual", c.actual());
        m.put("difference", c.difference());
        m.put("explanation", c.explanation());
        m.put("difference_transaction_id", c.differenceTransactionId());
        m.put("cashier", c.cashier());
        m.put("closed_at", c.closedAt());
        m.put("status", c.status());
        m.put("reopen_reason", c.reopenReason());
        m.put("reopened_at", c.reopenedAt());
        return m;
    }

    /** Dernier jour clôturé du compte (verrouillé jusqu'à cette date incluse), ou null. */
    public LocalDate lockedThrough(long accountId) {
        List<LocalDate> max = jdbc.query("SELECT MAX(closing_date) AS d FROM cash_closing"
                + " WHERE account_id = ? AND status = 'CLOSED'", row -> row.localDate("d"), accountId);
        return max.isEmpty() ? null : max.get(0);              // MAX sans ligne renvoie une ligne NULL
    }

    public Optional<CashClosing> findClosed(long accountId, LocalDate day) {
        return jdbc.queryOne("SELECT * FROM cash_closing WHERE account_id = ? AND closing_date = ? AND status = 'CLOSED'",
                mapper(), accountId, day);
    }

    public List<CashClosing> findForDate(LocalDate day) {
        return jdbc.query("SELECT * FROM cash_closing WHERE closing_date = ? ORDER BY id DESC", mapper(), day);
    }

    public void linkDifference(long closingId, long txId) {
        jdbc.update("UPDATE cash_closing SET difference_transaction_id = ?, version = version + 1, updated_at = ?"
                + " WHERE id = ? AND difference_transaction_id IS NULL", txId, clock.instant(), closingId);
    }
}
