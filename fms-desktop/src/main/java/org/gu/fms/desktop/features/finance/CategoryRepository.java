package org.gu.fms.desktop.features.finance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.domain.finance.SystemCategory;
import org.gu.fms.domain.finance.TransactionCategory;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.finance.TransactionNature;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class CategoryRepository extends JdbcRepository<TransactionCategory> {

    @Inject
    public CategoryRepository(Jdbc jdbc, Clock clock) {
        super(jdbc, clock);
    }

    @Override
    protected String table() {
        return "transaction_category";
    }

    @Override
    protected String defaultOrder() {
        return "direction, system DESC, name COLLATE NOCASE";
    }

    @Override
    protected RowMapper<TransactionCategory> mapper() {
        return row -> new TransactionCategory(row.longValue("id"), row.longValue("version"), row.string("code"),
                row.string("name"), row.enumValue("direction", TransactionDirection.class),
                row.enumValue("nature", TransactionNature.class), row.bool("direct_entry"),
                row.bool("requires_vehicle"), row.bool("system"), row.bool("active"));
    }

    @Override
    protected Long idOf(TransactionCategory c) {
        return c.id();
    }

    @Override
    protected long versionOf(TransactionCategory c) {
        return c.version();
    }

    @Override
    protected Map<String, Object> columnsOf(TransactionCategory c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", c.code());
        m.put("name", c.name());
        m.put("direction", c.direction());
        m.put("nature", c.nature());
        m.put("direct_entry", c.directEntry());
        m.put("requires_vehicle", c.requiresVehicle());
        m.put("system", c.system());
        m.put("active", c.active());
        return m;
    }

    /** Catégorie système (créée par la migration : toujours présente). */
    public TransactionCategory system(SystemCategory category) {
        return jdbc.queryOne("SELECT * FROM transaction_category WHERE code = ?", mapper(), category.code())
                .orElseThrow(() -> new IllegalStateException("Catégorie système absente : " + category.code()));
    }

    public boolean isUsed(long categoryId) {
        return jdbc.exists("SELECT 1 FROM financial_transaction WHERE category_id = ? LIMIT 1", categoryId);
    }
}
