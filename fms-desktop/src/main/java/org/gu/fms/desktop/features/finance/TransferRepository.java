package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.finance.AccountTransfer;
import org.gu.fms.domain.finance.TransferStatus;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class TransferRepository extends JdbcRepository<AccountTransfer> {

    private final AppSettings settings;

    @Inject
    public TransferRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "account_transfer";
    }

    @Override
    protected String defaultOrder() {
        return "transfer_date DESC, id DESC";
    }

    @Override
    protected RowMapper<AccountTransfer> mapper() {
        var c = settings.currency();
        return row -> new AccountTransfer(row.longValue("id"), row.longValue("version"), row.localDate("transfer_date"),
                row.longValue("from_account_id"), row.longValue("to_account_id"), row.money("amount", c),
                row.money("fee", c), row.string("label"), row.string("reference"), row.longOrNull("out_transaction_id"),
                row.longOrNull("in_transaction_id"), row.longOrNull("fee_transaction_id"),
                row.enumValue("status", TransferStatus.class), row.string("reversal_reason"), row.string("user_name"));
    }

    @Override
    protected Long idOf(AccountTransfer t) {
        return t.id();
    }

    @Override
    protected long versionOf(AccountTransfer t) {
        return t.version();
    }

    @Override
    protected Map<String, Object> columnsOf(AccountTransfer t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("transfer_date", t.date());
        m.put("from_account_id", t.fromAccountId());
        m.put("to_account_id", t.toAccountId());
        m.put("amount", t.amount());
        m.put("fee", t.fee());
        m.put("label", t.label());
        m.put("reference", t.reference());
        m.put("out_transaction_id", t.outTransactionId());
        m.put("in_transaction_id", t.inTransactionId());
        m.put("fee_transaction_id", t.feeTransactionId());
        m.put("status", t.status());
        m.put("reversal_reason", t.reversalReason());
        m.put("user_name", t.userName());
        return m;
    }
}
