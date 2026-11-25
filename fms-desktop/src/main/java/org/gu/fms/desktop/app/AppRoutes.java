package org.gu.fms.desktop.app;

import org.gu.fms.desktop.features.cash.CashModuleScreen;
import org.gu.fms.desktop.features.collection.CollectionsModuleScreen;
import org.gu.fms.desktop.features.common.ComingSoonScreen;
import org.gu.fms.desktop.features.dashboard.DashboardScreen;
import org.gu.fms.desktop.features.document.DocumentsScreen;
import org.gu.fms.desktop.features.driver.DriversModuleScreen;
import org.gu.fms.desktop.features.finance.AccountsModuleScreen;
import org.gu.fms.desktop.features.finance.TransactionsModuleScreen;
import org.gu.fms.desktop.features.finance.TransactionsScreen;
import org.gu.fms.desktop.features.loan.LoansScreen;
import org.gu.fms.desktop.features.maintenance.MaintenanceModuleScreen;
import org.gu.fms.desktop.features.payroll.PayrollModuleScreen;
import org.gu.fms.desktop.features.report.ReportsScreen;
import org.gu.fms.desktop.features.settings.SettingsScreen;
import org.gu.fms.desktop.features.vehicle.VehicleScreen;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.nav.Route;
import org.gu.fms.desktop.platform.ui.nav.RouteRegistry;

import java.util.List;

/**
 * Plan de navigation de l'application. Le menu latéral est généré à partir de cette liste, filtrée selon les modules
 * ouverts à l'utilisateur connecté. L'identifiant d'une route est aussi le nom du droit d'accès (user_permission).
 */
public final class AppRoutes {

    public static final String DASHBOARD = "dashboard";
    public static final String CASH = "cash";
    public static final String COLLECTIONS = "collections";
    public static final String TRANSACTIONS = "transactions";
    public static final String ACCOUNTS = "accounts";
    public static final String PAYROLL = "payroll";
    public static final String VEHICLES = "vehicles";
    public static final String DRIVERS = "drivers";
    public static final String MAINTENANCE = "maintenance";
    public static final String DOCUMENTS = "documents";
    public static final String LOANS = "loans";
    public static final String REPORTS = "reports";
    public static final String SETTINGS = "settings";

    private static final String OPERATIONS = "nav.section.operations";
    private static final String FLEET = "nav.section.fleet";
    private static final String FINANCE = "nav.section.finance";
    private static final String ANALYSIS = "nav.section.analysis";

    private AppRoutes() {
    }

    public static RouteRegistry registry() {
        return new RouteRegistry(List.of(
                new Route(DASHBOARD, "nav.dashboard", AppIcon.DASHBOARD, OPERATIONS, DashboardScreen.class),
                new Route(CASH, "nav.cash", AppIcon.CASH_REGISTER, OPERATIONS, CashModuleScreen.class),
                new Route(COLLECTIONS, "nav.collections", AppIcon.COLLECTIONS, OPERATIONS, CollectionsModuleScreen.class),
                new Route(TRANSACTIONS, "nav.transactions", AppIcon.TRANSACTIONS, OPERATIONS, TransactionsScreen.class),

                new Route(VEHICLES, "nav.vehicles", AppIcon.VEHICLE, FLEET, VehicleScreen.class),
                new Route(DRIVERS, "nav.drivers", AppIcon.DRIVERS, FLEET, DriversModuleScreen.class),
                new Route(MAINTENANCE, "nav.maintenance", AppIcon.MAINTENANCE, FLEET, MaintenanceModuleScreen.class),
                new Route(DOCUMENTS, "nav.documents", AppIcon.DOCUMENT, FLEET, DocumentsScreen.class),

                new Route(ACCOUNTS, "nav.accounts", AppIcon.ACCOUNTS, FINANCE, AccountsModuleScreen.class),
                new Route(PAYROLL, "nav.payroll", AppIcon.PAYROLL, FINANCE, PayrollModuleScreen.class),
                new Route(LOANS, "nav.loans", AppIcon.LOAN, FINANCE, LoansScreen.class),

                new Route(REPORTS, "nav.reports", AppIcon.REPORTS, ANALYSIS, ReportsScreen.class),

                new Route(SETTINGS, "nav.settings", AppIcon.SETTINGS, Route.FOOTER, SettingsScreen.class)));
    }
}
