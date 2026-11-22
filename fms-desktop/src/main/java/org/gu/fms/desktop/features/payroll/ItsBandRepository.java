package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.payroll.ItsBand;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public final class ItsBandRepository extends JdbcRepository<ItsBand> {

    private final AppSettings settings;

    @Inject
    public ItsBandRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "its_band";
    }

    @Override
    protected String defaultOrder() {
        return "effective_from DESC, (up_to IS NULL), up_to";
    }

    @Override
    protected RowMapper<ItsBand> mapper() {
        return row -> new ItsBand(row.longValue("id"), row.longValue("version"), row.localDate("effective_from"),
                row.money("up_to", settings.currency()), row.percent("rate_bp"),
                row.money("fixed_amount", settings.currency()));
    }

    @Override
    protected Long idOf(ItsBand b) {
        return b.id();
    }

    @Override
    protected long versionOf(ItsBand b) {
        return b.version();
    }

    @Override
    protected Map<String, Object> columnsOf(ItsBand b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("effective_from", b.effectiveFrom());
        m.put("up_to", b.upTo());
        m.put("rate_bp", b.rate());
        m.put("fixed_amount", b.fixedAmount());
        return m;
    }

    public List<ItsBand> findByEffectiveFrom(LocalDate effectiveFrom) {
        return jdbc.query("SELECT * FROM its_band WHERE effective_from = ?", mapper(), effectiveFrom);
    }
}
