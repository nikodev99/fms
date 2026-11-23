package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.DriverBalance;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.Driver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lecture des comptes chauffeurs pour l'écran : tous les chauffeurs non partis, et les partis qui doivent encore
 * quelque chose. Les mouvements passent par {@link DriverLedgerService}.
 */
@Singleton
public final class DriverAccountService implements CrudService<DriverAccountRow> {

    private final DriverRepository drivers;
    private final DriverLedgerService ledger;
    private final AppSettings settings;

    @Inject
    public DriverAccountService(DriverRepository drivers, DriverLedgerService ledger, AppSettings settings) {
        this.drivers = drivers;
        this.ledger = ledger;
        this.settings = settings;
    }

    @Override
    public List<DriverAccountRow> findAll() {
        Map<Long, DriverBalance> balances = ledger.balances();
        List<DriverAccountRow> rows = new ArrayList<>();
        for (Driver d : drivers.findAll()) {
            DriverBalance b = balances.getOrDefault(d.id(), DriverBalance.zero(d.id(), settings.currency()));
            if (d.status() != Driver.DriverStatus.LEFT || !b.totalDue().isZero() || !b.credit().isZero()) {
                rows.add(new DriverAccountRow(d, b));
            }
        }
        return rows;
    }

    @Override
    public DriverAccountRow save(DriverAccountRow entity) {
        throw ValidationException.of(FieldError.of(null, "ledger.readOnly"));
    }
}
