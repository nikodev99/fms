package org.gu.fms.desktop.app;

import org.gu.fms.desktop.features.common.ComingSoonScreen;
import org.gu.fms.desktop.features.dashboard.DashboardScreen;
import org.gu.fms.desktop.features.driver.DriversModuleScreen;
import org.gu.fms.desktop.features.maintenance.MaintenanceModuleScreen;
import org.gu.fms.desktop.features.settings.SettingsScreen;
import org.gu.fms.desktop.features.vehicle.VehicleScreen;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.nav.Route;
import org.gu.fms.desktop.platform.ui.nav.RouteRegistry;

import java.util.List;

/**
 * Plan de navigation de l'application. Le menu latéral est généré à partir de cette liste.
 * Un module pas encore construite pointe vers {@link ComingSoonScreen} : il est visible, et clairement
 * signalé comme non disponible.
 */
public class AppRoutes {
    public static final String DASHBOARD = "dashboard";
    public static final String CASH = "cash";
    public static final String COLLECTIONS = "collections";
    public static final String VEHICLES = "vehicles";
    public static final String DRIVERS = "drivers";
    public static final String MAINTENANCE = "maintenance";
    public static final String SETTINGS = "settings";

    private static final String OPERATIONS = "nav.section.operations";
    private static final String FLEET = "nav.section.fleet";
    private static final String FINANCE = "nav.section.finance";
    private static final String ANALYSIS = "nav.section.analysis";

    public AppRoutes() {
    }

    public static RouteRegistry registry() {
        return new RouteRegistry(List.of(
                new Route(DASHBOARD, "nav.dashboard", AppIcon.DASHBOARD, OPERATIONS, DashboardScreen.class),
                new Route(CASH, "nav.cash", AppIcon.CASH_REGISTER, OPERATIONS, ComingSoonScreen.class),
                new Route(COLLECTIONS, "nav.collections", AppIcon.COLLECTIONS, OPERATIONS, ComingSoonScreen.class),
                new Route("transactions", "nav.transactions", AppIcon.TRANSACTIONS, OPERATIONS, ComingSoonScreen.class),

                new Route(VEHICLES, "nav.vehicles", AppIcon.VEHICLE, FLEET, VehicleScreen.class),
                new Route(DRIVERS, "nav.drivers", AppIcon.DRIVERS, FLEET, DriversModuleScreen.class),
                new Route(MAINTENANCE, "nav.maintenance", AppIcon.MAINTENANCE, FLEET, MaintenanceModuleScreen.class),

                new Route("accounts", "nav.accounts", AppIcon.ACCOUNTS, FINANCE, ComingSoonScreen.class),
                new Route("payroll", "nav.payroll", AppIcon.PAYROLL, FINANCE, ComingSoonScreen.class),
                new Route("loans", "nav.loans", AppIcon.LOAN, FINANCE, ComingSoonScreen.class),

                new Route("reports", "nav.reports", AppIcon.REPORTS, ANALYSIS, ComingSoonScreen.class),
                new Route("simulations", "nav.simulations", AppIcon.SIMULATION, ANALYSIS, ComingSoonScreen.class),

                new Route(SETTINGS, "nav.settings", AppIcon.SETTINGS, Route.FOOTER, SettingsScreen.class)
        ));
    }
}
