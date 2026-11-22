package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.DriverBalance;
import org.gu.fms.domain.collection.DriverLedgerEntry;
import org.gu.fms.domain.collection.LedgerRubric;
import org.gu.fms.domain.collection.LedgerSource;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Comptes chauffeurs : insertion seule ; les soldes sont la somme des mouvements. */
@Singleton
public final class DriverLedgerRepository extends JdbcRepository<DriverLedgerEntry> {

    private final AppSettings settings;

    @Inject
    public DriverLedgerRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "driver_ledger";
    }

    @Override
    protected String defaultOrder() {
        return "entry_date DESC, id DESC";
    }

    @Override
    protected RowMapper<DriverLedgerEntry> mapper() {
        return row -> new DriverLedgerEntry(row.longValue("id"), row.longValue("version"), row.localDate("entry_date"),
                row.instant("recorded_at"), row.longValue("driver_id"), row.enumValue("rubric", LedgerRubric.class),
                row.money("amount", settings.currency()), row.enumValue("source", LedgerSource.class),
                row.longOrNull("source_id"), row.string("label"), row.string("user_name"));
    }

    @Override
    protected Long idOf(DriverLedgerEntry e) {
        return e.id();
    }

    @Override
    protected long versionOf(DriverLedgerEntry e) {
        return e.version();
    }

    @Override
    protected Map<String, Object> columnsOf(DriverLedgerEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entry_date", e.date());
        m.put("recorded_at", e.recordedAt());
        m.put("driver_id", e.driverId());
        m.put("rubric", e.rubric());
        m.put("amount", e.amount());
        m.put("source", e.source());
        m.put("source_id", e.sourceId());
        m.put("label", e.label());
        m.put("user_name", e.userName());
        return m;
    }

    public List<DriverLedgerEntry> findForDriver(long driverId) {
        return jdbc.query("SELECT * FROM driver_ledger WHERE driver_id = ? ORDER BY " + defaultOrder(), mapper(),
                driverId);
    }

    public List<DriverLedgerEntry> findBySource(LedgerSource source, long sourceId) {
        return jdbc.query("SELECT * FROM driver_ledger WHERE source = ? AND source_id = ? ORDER BY id", mapper(),
                source, sourceId);
    }

    public DriverBalance balance(long driverId) {
        return balances().getOrDefault(driverId, DriverBalance.zero(driverId, settings.currency()));
    }

    /** Soldes par chauffeur et par rubrique (chauffeurs ayant au moins un mouvement). */
    public Map<Long, DriverBalance> balances() {
        Map<Long, long[]> sums = new LinkedHashMap<>();
        jdbc.query("SELECT driver_id, rubric, SUM(amount) AS total FROM driver_ledger GROUP BY driver_id, rubric",
                row -> {
                    long[] s = sums.computeIfAbsent(row.longValue("driver_id"), k -> new long[3]);
                    s[row.enumValue("rubric", LedgerRubric.class).ordinal()] = row.longValue("total");
                    return null;
                });
        var c = settings.currency();
        Map<Long, DriverBalance> result = new LinkedHashMap<>();
        sums.forEach((driver, s) -> result.put(driver, new DriverBalance(driver,
                Money.of(s[LedgerRubric.SHORTAGE.ordinal()], c), Money.of(s[LedgerRubric.ADVANCE.ordinal()], c),
                Money.of(s[LedgerRubric.OTHER_DEBT.ordinal()], c))));
        return result;
    }
}
