package org.gu.fms.desktop.features.dashboard;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.collection.CollectionCalendarService;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.AccountRepository;
import org.gu.fms.desktop.features.finance.CategoryRepository;
import org.gu.fms.desktop.features.finance.Ledger;
import org.gu.fms.desktop.features.finance.TransactionRepository;
import org.gu.fms.desktop.features.payroll.PayslipRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.domain.collection.CalendarState;
import org.gu.fms.domain.collection.CollectionCalendar;
import org.gu.fms.domain.collection.CollectionPolicy;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.TransactionCategory;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.payroll.Payslip;
import org.gu.fms.domain.report.OperatingResult;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.VehicleStatus;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chiffres du tableau de bord pour une période (un mois, ou tout l'historique) : versements et recouvrement,
 * résultat d'exploitation ({@link OperatingResult}), trésorerie consolidée (à ce jour), flotte, et les douze derniers
 * mois pour les graphiques.
 */
@Singleton
public final class DashboardService {

    public static final int CHART_MONTHS = 12;

    private final CollectionCalendarService calendar;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final PayslipRepository payslips;
    private final AccountRepository accounts;
    private final Ledger ledger;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;

    @Inject
    public DashboardService(CollectionCalendarService calendar, TransactionRepository transactions,
                            CategoryRepository categories, PayslipRepository payslips, AccountRepository accounts,
                            Ledger ledger, VehicleRepository vehicles, DriverRepository drivers) {
        this.calendar = calendar;
        this.transactions = transactions;
        this.categories = categories;
        this.payslips = payslips;
        this.accounts = accounts;
        this.ledger = ledger;
        this.vehicles = vehicles;
        this.drivers = drivers;
    }

    public LocalDate today() {
        return calendar.today();
    }

    /** Point du mois : agrégats par mois pour les graphiques. */
    public record MonthPoint(YearMonth month, long expected, long received, OperatingResult result) {
    }

    public record Data(LocalDate from, LocalDate to, CollectionCalendar.Summary collections, CollectionPolicy policy,
                       OperatingResult result, long consolidatedCash, int countedAccounts, int vehiclesInService,
                       int vehiclesBroken, int vehiclesTotal, int activeDrivers, List<MonthPoint> months,
                       List<FinancialTransaction> transactions) {
    }

    /** @param month mois affiché ; null = depuis le début de l'activité */
    public Data load(YearMonth month) {
        LocalDate today = today();
        LocalDate from = month == null ? calendar.firstActivity().withDayOfMonth(1) : month.atDay(1);
        LocalDate to = month == null ? today : month.atEndOfMonth();
        Map<Long, TransactionDirection> ways = categories.findAll().stream()
                .collect(Collectors.toMap(TransactionCategory::id, TransactionCategory::direction));

        CollectionCalendar.Month period = calendar.period(from, to);
        List<FinancialTransaction> txs = transactions.findBetween(from, to);
        List<Payslip> slips = payslips.findBetween(YearMonth.from(from), YearMonth.from(to));
        OperatingResult result = OperatingResult.of(txs, ways, slips);

        Map<Long, Money> balances = ledger.balances();
        long cash = 0;
        int counted = 0;
        for (FinancialAccount a : accounts.findAll()) {
            if (a.active() && a.includedInConsolidated()) {
                Money b = balances.get(a.id());
                cash += b == null ? 0 : b.minor();
                counted++;
            }
        }

        int inService = 0;
        int broken = 0;
        int total = 0;
        for (Vehicle v : vehicles.findAll()) {
            if (v.status() == VehicleStatus.SOLD || v.status() == VehicleStatus.INACTIVE) {
                continue;
            }
            total++;
            if (v.status() == VehicleStatus.ACTIVE) {
                inService++;
            } else if (v.status() == VehicleStatus.UNDER_MAINTENANCE || v.status() == VehicleStatus.IMMOBILISED) {
                broken++;
            }
        }
        int activeDrivers = (int) drivers.findAll().stream().map(Driver::status)
                .filter(s -> s == Driver.DriverStatus.ACTIVE).count();

        YearMonth last = YearMonth.from(to);
        List<MonthPoint> points = months(last.minusMonths(CHART_MONTHS - 1L), last, today, ways);
        List<FinancialTransaction> listed = new ArrayList<>(txs);
        listed.sort((a, b) -> !b.date().isEqual(a.date()) ? b.date().compareTo(a.date())
                : Long.compare(b.id(), a.id()));
        return new Data(from, to, period.summary(), calendar.policy(), result, cash, counted, inService, broken, total,
                activeDrivers, points, listed);
    }

    private List<MonthPoint> months(YearMonth first, YearMonth last, LocalDate today,
                                    Map<Long, TransactionDirection> ways) {
        LocalDate from = first.atDay(1);
        LocalDate to = last.atEndOfMonth();
        Map<YearMonth, long[]> collected = new LinkedHashMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            collected.put(m, new long[2]);
        }
        for (CollectionCalendar.DriverRow row : calendar.period(from, to).rows()) {
            for (CollectionCalendar.Cell c : row.cells()) {
                if (c.date().isAfter(today)) {
                    continue;
                }
                long[] acc = collected.get(YearMonth.from(c.date()));
                if (c.state() != CalendarState.PENDING) {
                    acc[0] += c.expected();
                }
                acc[1] += c.received();
            }
        }
        Map<YearMonth, List<FinancialTransaction>> txByMonth = transactions.findBetween(from, to).stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.date())));
        Map<YearMonth, List<Payslip>> slipsByMonth = payslips.findBetween(first, last).stream()
                .collect(Collectors.groupingBy(Payslip::period));
        List<MonthPoint> points = new ArrayList<>();
        collected.forEach((m, acc) -> points.add(new MonthPoint(m, acc[0], acc[1],
                OperatingResult.of(txByMonth.getOrDefault(m, List.of()), ways, slipsByMonth.getOrDefault(m, List.of())))));
        return points;
    }
}
