package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.driver.AssignmentRepository;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.Ledger;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.*;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.driver.DriverAssignment.AssignmentRole;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Point journalier (instructions §4.3), identique à l'édition Excel : pour chaque chauffeur concerné ce jour-là
 * (affectation active ou versement reçu), attendu et reçu sont figés et l'écart est porté à son compte.
 * La validation ne traite que les chauffeurs pas encore pointés : un chauffeur qui verse après coup peut être
 * pointé ensuite sans toucher aux autres.
 */
@Singleton
public final class DailyPointService {

    static final String AUDIT_TYPE = "daily_point";

    private final DailyPointRepository points;
    private final CollectionRepository collections;
    private final AssignmentRepository assignments;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final DriverLedgerService driverLedger;
    private final Ledger ledger;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final Clock clock;
    private final AppSettings settings;
    private final Formats formats;

    @Inject
    public DailyPointService(DailyPointRepository points, CollectionRepository collections,
                             AssignmentRepository assignments, DriverRepository drivers, VehicleRepository vehicles,
                             DriverLedgerService driverLedger, Ledger ledger, TransactionManager tx, AuditLog audit,
                             Clock clock, AppSettings settings, Formats formats) {
        this.points = points;
        this.collections = collections;
        this.assignments = assignments;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.driverLedger = driverLedger;
        this.ledger = ledger;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.settings = settings;
        this.formats = formats;
    }

    /** Lignes du point de la journée : chauffeurs pointés et chauffeurs à pointer. */
    public List<PointRow> preview(LocalDate day) {
        Money zero = Money.zero(settings.currency());
        Map<Long, Long> expected = new LinkedHashMap<>();
        Map<Long, Long> vehicleOf = new LinkedHashMap<>();
        for (DriverAssignment a : assignments.findActiveOn(day)) {
            expected.merge(a.driverId(), a.expectedMinor(), Long::sum);
            if (a.role() == AssignmentRole.PRIMARY || !vehicleOf.containsKey(a.driverId())) {
                vehicleOf.put(a.driverId(), a.vehicleId());
            }
        }
        Map<Long, Long> received = collections.receivedByDriver(day);
        collections.firstVehicleByDriver(day).forEach(vehicleOf::putIfAbsent);
        Map<Long, DailyPoint> done = points.findForDate(day).stream()
                .collect(Collectors.toMap(DailyPoint::driverId, Function.identity()));
        Map<Long, Vehicle> vehicleById = vehicles.findAll().stream()
                .collect(Collectors.toMap(Vehicle::id, Function.identity()));

        Set<Long> driverIds = new LinkedHashSet<>();
        expected.forEach((driver, amount) -> {
            if (amount > 0) {
                driverIds.add(driver);
            }
        });
        driverIds.addAll(received.keySet());
        driverIds.addAll(done.keySet());

        boolean isToday = day.equals(ledger.today());
        List<PointRow> rows = new ArrayList<>();
        for (Long driver : driverIds) {
            Long vehicleId = done.containsKey(driver) && done.get(driver).vehicleId() != null
                    ? done.get(driver).vehicleId() : vehicleOf.get(driver);
            Vehicle v = vehicleId == null ? null : vehicleById.get(vehicleId);
            boolean unavailable = isToday && v != null && (v.status() == VehicleStatus.UNDER_MAINTENANCE
                    || v.status() == VehicleStatus.IMMOBILISED || v.status() == VehicleStatus.TEMPORARILY_UNAVAILABLE);
            rows.add(new PointRow(day, driver, vehicleId, Money.of(expected.getOrDefault(driver, 0L), zero.currency()),
                    Money.of(received.getOrDefault(driver, 0L), zero.currency()), unavailable,
                    DaySituation.NORMAL, null, done.get(driver)));
        }
        return rows;
    }

    /** Nombre de chauffeurs restant à pointer pour la journée. */
    public long pendingCount(LocalDate day) {
        return preview(day).stream().filter(r -> !r.validated()).count();
    }

    /**
     * Valide le point des chauffeurs pas encore pointés. {@code decisions} : situation et motif par chauffeur
     * (absent = situation normale). Renvoie le nombre de chauffeurs pointés.
     */
    public int validate(LocalDate day, Map<Long, PointRow> decisions) {
        if (day == null) {
            throw ValidationException.of(FieldError.of("date", "validation.required"));
        }
        if (day.isAfter(ledger.today())) {
            throw ValidationException.of(FieldError.of("date", "dailyPoint.future"));
        }
        return tx.inTransaction(c -> {
            List<FieldError> errors = new ArrayList<>();
            List<PointRow> todo = new ArrayList<>();
            for (PointRow row : preview(day)) {
                if (row.validated()) {
                    continue;
                }
                PointRow decided = decisions.get(row.driverId());
                PointRow r = decided == null ? row : row.decided(decided.situation(), decided.reason());
                List<FieldError> rowErrors = DailyPointRules.validate(r.situation(), r.reason());
                if (!rowErrors.isEmpty()) {
                    errors.add(FieldError.of(null, "dailyPoint.exemptReasonFor",
                            drivers.findById(r.driverId()).map(d -> d.displayName()).orElse("?")));
                }
                todo.add(r);
            }
            ValidationException.throwIfAny(errors);
            if (todo.isEmpty()) {
                throw ValidationException.of(FieldError.of(null, "dailyPoint.nothingToValidate"));
            }
            for (PointRow r : todo) {
                DailyPoint point = DailyPointRules.compute(day, r.driverId(), r.vehicleId(), r.plannedExpected(),
                        r.received(), r.situation(), r.reason(), ledger.user(), clock.instant());
                long id = points.insert(point);
                DailyPoint saved = point.withIdentity(id, 0);
                audit.created(AUDIT_TYPE, id, points.snapshot(saved));
                driverLedger.post(r.driverId(), day, LedgerRubric.SHORTAGE, DailyPointRules.shortageMovement(saved),
                        LedgerSource.DAILY_POINT, id, "Point du " + formats.date(day));
            }
            return todo.size();
        });
    }
}
