package org.gu.fms.desktop.features.cash;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.section.SectionedScreen;

import java.util.List;

/** Caisse du jour : point journalier des chauffeurs, puis clôture de chaque compte. */
public final class CashModuleScreen extends SectionedScreen {

    @Inject
    public CashModuleScreen(Injector injector, Messages messages) {
        super(injector, messages);
    }

    @Override
    protected String titleKey() {
        return "nav.cash";
    }

    @Override
    protected Layout layout() {
        return Layout.TABS;
    }

    @Override
    protected List<Section> sections() {
        return List.of(
                new Section(null, "cash.tab.point", AppIcon.POINT, DailyPointScreen.class),
                new Section(null, "cash.tab.closing", AppIcon.LOCK, ClosingScreen.class));
    }
}
