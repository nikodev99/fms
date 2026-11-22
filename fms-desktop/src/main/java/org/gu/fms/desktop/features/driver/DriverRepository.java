package org.gu.fms.desktop.features.driver;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.Driver.DriverStatus;
import org.gu.fms.domain.driver.MaritalStatus;
import org.gu.fms.domain.driver.PayScheme;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public final class DriverRepository extends JdbcRepository<Driver> {

    private final AppSettings settings;

    @Inject
    public DriverRepository(Jdbc jdbc, Clock clock, AppSettings settings) {
        super(jdbc, clock);
        this.settings = settings;
    }

    @Override
    protected String table() {
        return "driver";
    }

    @Override
    protected String defaultOrder() {
        return "last_name COLLATE NOCASE, first_name COLLATE NOCASE";
    }

    @Override
    protected RowMapper<Driver> mapper() {
        return row -> new Driver(
                row.longValue("id"),
                row.longValue("version"),
                row.string("last_name"),
                row.string("first_name"),
                row.string("phone"),
                row.string("address"),
                row.string("id_number"),
                row.string("licence_number"),
                row.localDate("licence_expiry"),
                row.localDate("hire_date"),
                row.enumValue("status", DriverStatus.class),
                row.enumValue("pay_scheme", PayScheme.class),
                row.money("fixed_salary", settings.currency()),
                row.percent("commission_bp"),
                row.money("daily_target", settings.currency()),
                row.money("deposit_agreed", settings.currency()),
                row.bool("cnss_subject"),
                row.string("cnss_number"),
                row.bool("its_subject"),
                row.enumValue("marital_status", MaritalStatus.class),
                row.intOrNull("dependent_children"),
                row.string("notes"));
    }

    @Override
    protected Long idOf(Driver d) {
        return d.id();
    }

    @Override
    protected long versionOf(Driver d) {
        return d.version();
    }

    @Override
    protected Map<String, Object> columnsOf(Driver d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("last_name", d.lastName());
        m.put("first_name", d.firstName());
        m.put("phone", d.phone());
        m.put("address", d.address());
        m.put("id_number", d.idNumber());
        m.put("licence_number", d.licenceNumber());
        m.put("licence_expiry", d.licenceExpiry());
        m.put("hire_date", d.hireDate());
        m.put("status", d.status());
        m.put("pay_scheme", d.payScheme());
        m.put("fixed_salary", d.fixedSalary());
        m.put("commission_bp", d.commissionRate());
        m.put("daily_target", d.dailyTarget());
        m.put("deposit_agreed", d.depositAgreed());
        m.put("cnss_subject", d.cnssSubject());
        m.put("cnss_number", d.cnssNumber());
        m.put("its_subject", d.itsSubject());
        m.put("marital_status", d.maritalStatus());
        m.put("dependent_children", d.dependentChildren());
        m.put("notes", d.notes());
        return m;
    }

    public boolean licenceTaken(String licence, Long excludingId) {
        return existsWithValue("licence_number", licence, excludingId);
    }
}