package org.gu.fms.desktop.features.alert;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.app.AppRoutes;
import org.gu.fms.desktop.features.collection.CollectionPolicyService;
import org.gu.fms.desktop.features.collection.DailyPointService;
import org.gu.fms.desktop.features.document.ComplianceRow;
import org.gu.fms.desktop.features.document.ComplianceService;
import org.gu.fms.desktop.features.loan.LoanInstalmentRepository;
import org.gu.fms.desktop.features.loan.LoanRepository;
import org.gu.fms.desktop.features.maintenance.MaintenanceEventRepository;
import org.gu.fms.desktop.features.maintenance.MaintenancePlanRepository;
import org.gu.fms.desktop.features.maintenance.MaintenanceTypeRepository;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.domain.collection.CollectionPolicy;
import org.gu.fms.domain.document.DocumentStatus;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.fleet.FleetPreferences;
import org.gu.fms.domain.loan.Loan;
import org.gu.fms.domain.loan.LoanInstalment;
import org.gu.fms.domain.maintenance.*;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recense ce qui demande une mise à jour : documents, plans d'entretien, échéances d'emprunt, points journaliers.
 *
 * Critique (bloquant) : document expiré d'un véhicule en service ou d'un chauffeur actif ; entretien en retard sur un
 * véhicule en service ; échéance d'emprunt dépassée et non payée. Le reste est un avertissement.
 */
@Singleton
public final class AlertService {

    /** Jours passés examinés pour les points journaliers en attente. */
    static final int POINT_LOOKBACK_DAYS = 7;

    private final ComplianceService compliance;
    private final MaintenancePlanRepository plans;
    private final MaintenanceEventRepository events;
    private final MaintenanceTypeRepository types;
    private final VehicleRepository vehicles;
    private final LoanRepository loans;
    private final LoanInstalmentRepository instalments;
    private final DailyPointService points;
    private final CollectionPolicyService collectionPolicy;
    private final FleetPreferencesService prefs;
    private final Messages messages;
    private final Formats formats;

    @Inject
    public AlertService(ComplianceService compliance, MaintenancePlanRepository plans, MaintenanceEventRepository events,
                        MaintenanceTypeRepository types, VehicleRepository vehicles, LoanRepository loans,
                        LoanInstalmentRepository instalments, DailyPointService points,
                        CollectionPolicyService collectionPolicy, FleetPreferencesService prefs, Messages messages,
                        Formats formats) {
        this.compliance = compliance;
        this.plans = plans;
        this.events = events;
        this.types = types;
        this.vehicles = vehicles;
        this.loans = loans;
        this.instalments = instalments;
        this.points = points;
        this.collectionPolicy = collectionPolicy;
        this.prefs = prefs;
        this.messages = messages;
        this.formats = formats;
    }

    /** Alertes du moment, les critiques d'abord, puis par date. */
    public List<AppAlert> collect() {
        LocalDate today = compliance.today();
        FleetPreferences p = prefs.load();
        List<AppAlert> alerts = new ArrayList<>();
        documents(alerts);
        maintenance(alerts, today, p);
        loans(alerts, today, p);
        dailyPoints(alerts, today);
        alerts.sort(Comparator.comparing(AppAlert::severity)
                .thenComparing(a -> a.date() == null ? LocalDate.MAX : a.date()));
        return List.copyOf(alerts);
    }

    private void documents(List<AppAlert> alerts) {
        for (ComplianceRow r : compliance.rows()) {
            if (!r.needsAttention()) {
                continue;
            }
            boolean critical = r.status() == DocumentStatus.EXPIRED && r.ownerInService();
            AppAlert.Severity severity = critical ? AppAlert.Severity.CRITICAL : AppAlert.Severity.WARNING;
            String title = messages.get("alert.document." + r.status().name().toLowerCase(java.util.Locale.ROOT),
                    r.type().name(), r.displayLabel());
            String detail = r.document() == null || r.document().expiresOn() == null ? messages.get("alert.document.missingDetail")
                    : r.status() == DocumentStatus.EXPIRED
                    ? messages.get("alert.document.expiredDetail", formats.date(r.document().expiresOn()))
                    : messages.get("alert.document.expiringDetail", formats.date(r.document().expiresOn()), r.daysLeft());
            boolean vehicle = r.subject() == DocumentType.DocumentSubject.VEHICLE;
            alerts.add(new AppAlert("doc:" + r.key(), severity, AppAlert.Kind.DOCUMENT, AppRoutes.DOCUMENTS, title,
                    detail, vehicle ? r.ownerId() : null, vehicle ? null : r.ownerId(),
                    r.document() == null ? null : r.document().expiresOn()));
        }
    }

    private void maintenance(List<AppAlert> alerts, LocalDate today, FleetPreferences p) {
        Map<Long, Vehicle> vehicleById = vehicles.findAll().stream()
                .collect(Collectors.toMap(Vehicle::id, Function.identity()));
        Map<Long, MaintenanceType> typeById = types.findAll().stream()
                .collect(Collectors.toMap(MaintenanceType::id, Function.identity()));
        List<MaintenanceEvent> history = events.findAll();
        for (MaintenancePlan plan : plans.findAll()) {
            Vehicle v = vehicleById.get(plan.vehicleId());
            if (v == null || !plan.active() || v.status() == Vehicle.VehicleStatus.SOLD || v.status() == Vehicle.VehicleStatus.INACTIVE) {
                continue;
            }
            MaintenancePlan e = MaintenancePlanRules.withHistory(plan, history);
            DueStatus s = MaintenancePlanRules.status(e, today, v.currentMileage(), p.maintenanceAlertDays(),
                    p.maintenanceAlertKm());
            if (s != DueStatus.OVERDUE && s != DueStatus.DUE_SOON) {
                continue;
            }
            boolean critical = s == DueStatus.OVERDUE && v.status() == Vehicle.VehicleStatus.ACTIVE;
            MaintenanceType t = typeById.get(plan.typeId());
            String due = e.nextDueDate() == null ? "" : formats.date(e.nextDueDate());
            String km = e.nextDueKm() == null ? "" : formats.number(e.nextDueKm()) + " km";
            alerts.add(new AppAlert("plan:" + plan.id(), critical ? AppAlert.Severity.CRITICAL : AppAlert.Severity.WARNING,
                    AppAlert.Kind.MAINTENANCE, AppRoutes.MAINTENANCE,
                    messages.get(s == DueStatus.OVERDUE ? "alert.maintenance.overdue" : "alert.maintenance.soon",
                            t == null ? "" : t.name(), v.registration()),
                    messages.get("alert.maintenance.detail", String.join(" / ",
                            java.util.stream.Stream.of(due, km).filter(x -> !x.isEmpty()).toList())),
                    v.id(), null, e.nextDueDate()));
        }
    }

    private void loans(List<AppAlert> alerts, LocalDate today, FleetPreferences p) {
        Map<Long, Loan> loanById = loans.findAll().stream().collect(Collectors.toMap(Loan::id, Function.identity()));
        for (LoanInstalment i : instalments.findDueOfActiveLoans()) {
            boolean overdue = i.dueDate().isBefore(today);
            if (!overdue && i.dueDate().isAfter(today.plusDays(p.loanAlertDays()))) {
                continue;
            }
            Loan l = loanById.get(i.loanId());
            alerts.add(new AppAlert("loan:" + i.id(), overdue ? AppAlert.Severity.CRITICAL : AppAlert.Severity.WARNING,
                    AppAlert.Kind.LOAN, AppRoutes.LOANS,
                    messages.get(overdue ? "alert.loan.overdue" : "alert.loan.soon", i.seq(), l == null ? "" : l.label()),
                    messages.get("alert.loan.detail", formats.money(i.total()), formats.date(i.dueDate())),
                    null, null, i.dueDate()));
        }
    }

    private void dailyPoints(List<AppAlert> alerts, LocalDate today) {
        CollectionPolicy policy = collectionPolicy.load();
        int pending = 0;
        LocalDate oldest = null;
        for (int k = POINT_LOOKBACK_DAYS; k >= 1; k--) {
            LocalDate day = today.minusDays(k);
            long n = points.pendingCount(day);
            if (n > 0 && !(policy.isRestDay(day) && n == 0)) {
                pending += (int) n;
                oldest = oldest == null ? day : oldest;
            }
        }
        if (pending > 0) {
            alerts.add(new AppAlert("points", AppAlert.Severity.WARNING, AppAlert.Kind.DAILY_POINT, AppRoutes.CASH,
                    messages.get("alert.points", pending), messages.get("alert.points.detail", formats.date(oldest)),
                    null, null, oldest));
        }
    }
}
