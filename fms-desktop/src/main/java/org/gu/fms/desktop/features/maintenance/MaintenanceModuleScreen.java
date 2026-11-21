package org.gu.fms.desktop.features.maintenance;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.section.SectionedScreen;

import java.util.List;

/** Module Entretien : interventions | plans d'entretien | seuils de réparation. */
public final class MaintenanceModuleScreen extends SectionedScreen {

    @Inject
    public MaintenanceModuleScreen(Injector injector, Messages messages) {
        super(injector, messages);
    }

    @Override
    protected String titleKey() {
        return "nav.maintenance";
    }

    @Override
    protected Layout layout() {
        return Layout.TABS;
    }

    @Override
    protected List<Section> sections() {
        return List.of(
                new Section(null, "maintenance.tab.events", AppIcon.MAINTENANCE, MaintenanceEventsScreen.class),
                new Section(null, "maintenance.tab.plans", AppIcon.PLAN, MaintenancePlansScreen.class),
                new Section(null, "maintenance.tab.thresholds", AppIcon.THRESHOLD, RepairThresholdsScreen.class));
    }
}
