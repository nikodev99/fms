package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.payroll.ContributionGroup;
import org.gu.fms.domain.payroll.ContributionPayer;
import org.gu.fms.domain.payroll.PayrollContribution;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class PayrollContributionRepository extends JdbcRepository<PayrollContribution> {

    private final AppSettings settings;

    @Inject
    public PayrollContributionRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "payroll_contribution";
    }

    @Override
    protected String defaultOrder() {
        return "contribution_group, code, effective_from DESC";
    }

    @Override
    protected RowMapper<PayrollContribution> mapper() {
        return row -> new PayrollContribution(row.longValue("id"), row.longValue("version"), row.string("code"),
                row.string("name"), row.enumValue("contribution_group", ContributionGroup.class),
                row.enumValue("payer", ContributionPayer.class), row.percent("rate_bp"),
                row.money("monthly_ceiling", settings.currency()), row.money("monthly_floor", settings.currency()),
                row.bool("enabled"), row.localDate("effective_from"), row.string("legal_source"), row.bool("verified"));
    }

    @Override
    protected Long idOf(PayrollContribution c) {
        return c.id();
    }

    @Override
    protected long versionOf(PayrollContribution c) {
        return c.version();
    }

    @Override
    protected Map<String, Object> columnsOf(PayrollContribution c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", c.code());
        m.put("name", c.name());
        m.put("contribution_group", c.group());
        m.put("payer", c.payer());
        m.put("rate_bp", c.rate());
        m.put("monthly_ceiling", c.monthlyCeiling());
        m.put("monthly_floor", c.monthlyFloor());
        m.put("enabled", c.enabled());
        m.put("effective_from", c.effectiveFrom());
        m.put("legal_source", c.legalSource());
        m.put("verified", c.verified());
        return m;
    }

    public boolean versionExists(String code, LocalDate effectiveFrom, Long excludingId) {
        return excludingId == null
                ? jdbc.exists("SELECT 1 FROM payroll_contribution WHERE code = ? AND effective_from = ?", code, effectiveFrom)
                : jdbc.exists("SELECT 1 FROM payroll_contribution WHERE code = ? AND effective_from = ? AND id <> ?",
                code, effectiveFrom, excludingId);
    }
}
