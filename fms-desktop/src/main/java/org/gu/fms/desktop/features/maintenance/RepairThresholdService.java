package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.maintenance.RepairAssessment;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public final class RepairThresholdService implements CrudService<RepairRow> {

    private final VehicleRepository vehicles;
    private final MaintenanceEventRepository events;
    private final FleetPreferencesService prefs;
    private final AppSettings settings;

    @Inject
    public RepairThresholdService(VehicleRepository vehicles, MaintenanceEventRepository events,
                                  FleetPreferencesService prefs, AppSettings settings) {
        this.vehicles = vehicles;
        this.events = events;
        this.prefs = prefs;
        this.settings = settings;
    }

    /** Véhicules les plus proches de leur seuil en premier. */
    @Override
    public List<RepairRow> findAll() {
        Percent warn = prefs.load().repairWarningPercent();
        Map<Long, List<MaintenanceEvent>> byVehicle = events.findAll().stream()
                .collect(Collectors.groupingBy(MaintenanceEvent::vehicleId));
        List<RepairRow> rows = new ArrayList<>();
        for (Vehicle v : vehicles.findAll()) {
            List<MaintenanceEvent> list = byVehicle.getOrDefault(v.id(), List.of());
            long repairs = list.stream().filter(e -> e.counts() && e.kind() == MaintenanceEvent.MaintenanceKind.REPAIR).count();
            rows.add(new RepairRow(v, RepairAssessment.of(list, Money.zero(settings.currency()), v.repairThreshold(),
                    warn), repairs));
        }
        rows.sort(Comparator.comparing((RepairRow r) -> r.assessment().usedPercent(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    @Override
    public RepairRow save(RepairRow entity) {
        throw new UnsupportedOperationException("Écran en lecture seule");
    }
}