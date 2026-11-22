package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.finance.*;
import org.gu.fms.domain.money.Money;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grand livre de trésorerie. Les soldes sont toujours calculés ici, jamais stockés :
 * solde = solde d'ouverture du compte + Σ(entrées) − Σ(sorties).
 */
@Singleton
public final class TransactionRepository extends JdbcRepository<FinancialTransaction> {

    private static final String SIGNED = "CASE direction WHEN 'INFLOW' THEN amount ELSE -amount END";

    private final AppSettings settings;

    @Inject
    public TransactionRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "financial_transaction";
    }

    @Override
    protected String defaultOrder() {
        return "tx_date DESC, id DESC";
    }

    @Override
    protected RowMapper<FinancialTransaction> mapper() {
        return row -> new FinancialTransaction(row.longValue("id"), row.longValue("version"), row.localDate("tx_date"),
                row.instant("recorded_at"), row.enumValue("direction", TransactionDirection.class),
                row.longValue("category_id"), row.enumValue("nature", TransactionNature.class),
                row.longValue("account_id"), row.money("amount", settings.currency()), row.longOrNull("vehicle_id"),
                row.longOrNull("driver_id"), row.string("label"), row.string("reference"),
                row.enumValue("source", TransactionSource.class), row.longOrNull("source_id"),
                row.longOrNull("reversal_of_id"), row.longOrNull("reversed_by_id"), row.string("user_name"));
    }

    @Override
    protected Long idOf(FinancialTransaction t) {
        return t.id();
    }

    @Override
    protected long versionOf(FinancialTransaction t) {
        return t.version();
    }

    @Override
    protected Map<String, Object> columnsOf(FinancialTransaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tx_date", t.date());
        m.put("recorded_at", t.recordedAt());
        m.put("direction", t.direction());
        m.put("category_id", t.categoryId());
        m.put("nature", t.nature());
        m.put("account_id", t.accountId());
        m.put("amount", t.amount());
        m.put("vehicle_id", t.vehicleId());
        m.put("driver_id", t.driverId());
        m.put("label", t.label());
        m.put("reference", t.reference());
        m.put("source", t.source());
        m.put("source_id", t.sourceId());
        m.put("reversal_of_id", t.reversalOfId());
        m.put("reversed_by_id", t.reversedById());
        m.put("user_name", t.userName());
        return m;
    }

    public List<FinancialTransaction> findBetween(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM financial_transaction WHERE tx_date BETWEEN ? AND ? ORDER BY " + defaultOrder(),
                mapper(), from, to);
    }

    public List<FinancialTransaction> findBySource(TransactionSource source, long sourceId) {
        return jdbc.query("SELECT * FROM financial_transaction WHERE source = ? AND source_id = ? ORDER BY id",
                mapper(), source, sourceId);
    }

    /** Transactions d'une catégorie système (ex. avances), de la plus récente à la plus ancienne. */
    public List<FinancialTransaction> findByCategories(List<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(", ", java.util.Collections.nCopies(categoryIds.size(), "?"));
        return jdbc.query("SELECT * FROM financial_transaction WHERE category_id IN (" + marks + ") ORDER BY "
                + defaultOrder(), mapper(), categoryIds.toArray());
    }

    /** Marque la transaction comme contre-passée (seule modification permise après validation). */
    public void markReversed(long id, long reversalId) {
        jdbc.update("UPDATE financial_transaction SET reversed_by_id = ?, version = version + 1, updated_at = ?"
                + " WHERE id = ? AND reversed_by_id IS NULL", reversalId, clock.instant(), id);
    }

    /** Σ des mouvements du compte jusqu'au jour inclus (null = tous). */
    public long signedTotal(long accountId, LocalDate upTo) {
        if (upTo == null) {
            return jdbc.queryLong("SELECT COALESCE(SUM(" + SIGNED + "), 0) FROM financial_transaction WHERE account_id = ?",
                    accountId);
        }
        return jdbc.queryLong("SELECT COALESCE(SUM(" + SIGNED + "), 0) FROM financial_transaction"
                + " WHERE account_id = ? AND tx_date <= ?", accountId, upTo);
    }

    /** Soldes cumulés jour par jour à partir de {@code from} (mouvements seulement, sans solde d'ouverture). */
    public List<long[]> dailyTotalsFrom(long accountId, LocalDate from) {
        return jdbc.query("SELECT tx_date, SUM(" + SIGNED + ") AS total FROM financial_transaction"
                        + " WHERE account_id = ? AND tx_date >= ? GROUP BY tx_date ORDER BY tx_date",
                row -> new long[] {row.localDate("tx_date").toEpochDay(), row.longValue("total")}, accountId, from);
    }

    /** Totaux mouvements par compte (tous comptes), pour les soldes. */
    public Map<Long, Long> signedTotalsByAccount() {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT account_id, SUM(" + SIGNED + ") AS total FROM financial_transaction GROUP BY account_id",
                row -> result.put(row.longValue("account_id"), row.longValue("total")));
        return result;
    }

    /** Chiffres d'une journée pour un compte (virements séparés). */
    public ClosingFigures figures(long accountId, LocalDate day, Money openingBalanceOfAccount) {
        long before = signedTotal(accountId, day.minusDays(1));
        long[] sums = jdbc.queryOne("SELECT"
                        + " COALESCE(SUM(CASE WHEN nature <> 'TRANSFER' AND direction = 'INFLOW' THEN amount END), 0) AS inflow,"
                        + " COALESCE(SUM(CASE WHEN nature <> 'TRANSFER' AND direction = 'OUTFLOW' THEN amount END), 0) AS outflow,"
                        + " COALESCE(SUM(CASE WHEN nature = 'TRANSFER' AND direction = 'INFLOW' THEN amount END), 0) AS tin,"
                        + " COALESCE(SUM(CASE WHEN nature = 'TRANSFER' AND direction = 'OUTFLOW' THEN amount END), 0) AS tout"
                        + " FROM financial_transaction WHERE account_id = ? AND tx_date = ?",
                row -> new long[] {row.longValue("inflow"), row.longValue("outflow"), row.longValue("tin"),
                        row.longValue("tout")}, accountId, day).orElseThrow();
        var currency = settings.currency();
        return new ClosingFigures(openingBalanceOfAccount.plus(Money.of(before, currency)), Money.of(sums[0], currency),
                Money.of(sums[1], currency), Money.of(sums[2], currency), Money.of(sums[3], currency));
    }

    /** Montant payé (non annulé) pour un objet d'un module (ex. une intervention). */
    public long paidFor(TransactionSource source, long sourceId) {
        return jdbc.queryLong("SELECT COALESCE(SUM(amount), 0) FROM financial_transaction WHERE source = ? AND source_id = ?"
                + " AND reversal_of_id IS NULL AND reversed_by_id IS NULL", source, sourceId);
    }

    /** Montants payés par objet d'un module (non annulés). */
    public Map<Long, Long> paidBySource(TransactionSource source) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT source_id, SUM(amount) AS total FROM financial_transaction WHERE source = ?"
                        + " AND reversal_of_id IS NULL AND reversed_by_id IS NULL GROUP BY source_id",
                row -> result.put(row.longValue("source_id"), row.longValue("total")), source);
        return result;
    }
}
