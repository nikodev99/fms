package org.gu.fms.desktop.features.allocation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.allocation.AllocationBasis;
import org.gu.fms.domain.allocation.AllocationPurpose;
import org.gu.fms.domain.allocation.AllocationRule;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class AllocationRuleRepository extends JdbcRepository<AllocationRule> {

    private final AppSettings settings;

    @Inject
    public AllocationRuleRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "allocation_rule";
    }

    @Override
    protected String defaultOrder() {
        return "active DESC, name COLLATE NOCASE";
    }

    @Override
    protected RowMapper<AllocationRule> mapper() {
        return row -> new AllocationRule(row.longValue("id"), row.longValue("version"), row.string("name"),
                row.enumValue("purpose", AllocationPurpose.class), row.enumValue("basis", AllocationBasis.class),
                row.percent("rate_bp"), row.money("amount", settings.currency()), row.bool("active"),
                row.string("method"));
    }

    @Override
    protected Long idOf(AllocationRule r) {
        return r.id();
    }

    @Override
    protected long versionOf(AllocationRule r) {
        return r.version();
    }

    @Override
    protected Map<String, Object> columnsOf(AllocationRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.name());
        m.put("purpose", r.purpose());
        m.put("basis", r.basis());
        m.put("rate_bp", r.rate());
        m.put("amount", r.amount());
        m.put("active", r.active());
        m.put("method", r.method());
        return m;
    }
}
