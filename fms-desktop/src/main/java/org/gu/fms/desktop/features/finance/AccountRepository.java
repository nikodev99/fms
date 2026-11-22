package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.finance.AccountType;
import org.gu.fms.domain.finance.FinancialAccount;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class AccountRepository extends JdbcRepository<FinancialAccount> {

    private final AppSettings settings;

    @Inject
    public AccountRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "financial_account";
    }

    @Override
    protected String defaultOrder() {
        return "active DESC, CASE type WHEN 'CASH' THEN 0 WHEN 'MOBILE_MONEY' THEN 1 WHEN 'BANK' THEN 2 ELSE 3 END, name";
    }

    @Override
    protected RowMapper<FinancialAccount> mapper() {
        return row -> new FinancialAccount(row.longValue("id"), row.longValue("version"), row.string("name"),
                row.enumValue("type", AccountType.class), row.money("opening_balance", settings.currency()),
                row.localDate("opening_date"), row.bool("included_in_consolidated"), row.bool("overdraft_allowed"),
                row.bool("active"), row.string("notes"));
    }

    @Override
    protected Long idOf(FinancialAccount a) {
        return a.id();
    }

    @Override
    protected long versionOf(FinancialAccount a) {
        return a.version();
    }

    @Override
    protected Map<String, Object> columnsOf(FinancialAccount a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", a.name());
        m.put("type", a.type());
        m.put("opening_balance", a.openingBalance());
        m.put("opening_date", a.openingDate());
        m.put("included_in_consolidated", a.includedInConsolidated());
        m.put("overdraft_allowed", a.overdraftAllowed());
        m.put("active", a.active());
        m.put("notes", a.notes());
        return m;
    }

    public List<FinancialAccount> findActive() {
        return jdbc.query("SELECT * FROM financial_account WHERE active = 1 ORDER BY " + defaultOrder(), mapper());
    }

    /** Le compte a-t-il déjà des transactions ou des clôtures ? (solde d'ouverture figé) */
    public boolean hasMovements(long accountId) {
        return jdbc.exists("SELECT 1 FROM financial_transaction WHERE account_id = ? LIMIT 1", accountId)
                || jdbc.exists("SELECT 1 FROM cash_closing WHERE account_id = ? LIMIT 1", accountId);
    }
}
