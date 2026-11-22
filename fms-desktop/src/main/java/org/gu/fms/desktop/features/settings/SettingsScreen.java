package org.gu.fms.desktop.features.settings;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.gu.fms.desktop.features.allocation.AllocationRulesScreen;
import org.gu.fms.desktop.features.document.DocumentTypesScreen;
import org.gu.fms.desktop.features.maintenance.MaintenanceTypesScreen;
import org.gu.fms.desktop.features.payroll.ContributionsScreen;
import org.gu.fms.desktop.features.payroll.ItsSection;
import org.gu.fms.desktop.features.payroll.RecoverySection;
import org.gu.fms.desktop.features.vehicle.VehicleCategoriesScreen;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.section.SectionedScreen;

import java.util.List;

/** Paramètres : général, flotte, paie et provisions, données. */
public final class SettingsScreen extends SectionedScreen {

    private static final String GENERAL = "settings.group.general";
    private static final String FLEET = "settings.group.fleet";
    private static final String PAYROLL = "settings.group.payroll";
    private static final String DATA = "settings.group.data";

    @Inject
    public SettingsScreen(Injector injector, Messages messages) {
        super(injector, messages);
    }

    @Override
    protected String titleKey() {
        return "nav.settings";
    }

    @Override
    protected Layout layout() {
        return Layout.SIDE;
    }

    @Override
    protected List<Section> sections() {
        return List.of(
                new Section(GENERAL, "settings.company", AppIcon.COMPANY, CompanySection.class),
                new Section(GENERAL, "settings.regional", AppIcon.REGIONAL, RegionalSection.class),
                new Section(FLEET, "settings.categories", AppIcon.CATEGORY, VehicleCategoriesScreen.class),
                new Section(FLEET, "settings.documentTypes", AppIcon.DOCUMENT, DocumentTypesScreen.class),
                new Section(FLEET, "settings.maintenanceTypes", AppIcon.MAINTENANCE, MaintenanceTypesScreen.class),
                new Section(FLEET, "settings.fleetDefaults", AppIcon.DEFAULTS, FleetDefaultsSection.class),
                new Section(PAYROLL, "settings.contributions", AppIcon.CONTRIBUTIONS, ContributionsScreen.class),
                new Section(PAYROLL, "settings.its", AppIcon.TAX, ItsSection.class),
                new Section(PAYROLL, "settings.recoveries", AppIcon.ADVANCE, RecoverySection.class),
                new Section(PAYROLL, "settings.provisions", AppIcon.PROVISIONS, AllocationRulesScreen.class),
                new Section(DATA, "settings.backup", AppIcon.BACKUP_RESTORE, BackupScreen.class));
    }
}