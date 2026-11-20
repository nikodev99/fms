package org.gu.fms.desktop.features.driver;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.section.SectionedScreen;

import java.util.List;

/** Module Chauffeurs : liste des chauffeurs | affectations aux véhicules. */
public final class DriversModuleScreen extends SectionedScreen {

    @Inject
    public DriversModuleScreen(Injector injector, Messages messages) {
        super(injector, messages);
    }

    @Override
    protected String titleKey() {
        return "nav.drivers";
    }

    @Override
    protected Layout layout() {
        return Layout.TABS;
    }

    @Override
    protected List<Section> sections() {
        return List.of(
                new Section(null, "drivers.tab.list", AppIcon.DRIVERS, DriversScreen.class),
                new Section(null, "drivers.tab.assignments", AppIcon.ASSIGNMENT, AssignmentsScreen.class));
    }
}
