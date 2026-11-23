package org.gu.fms.desktop.features.collection;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.section.SectionedScreen;

import java.util.List;

/** Module Versements : versements reçus | comptes chauffeurs (manquants, avances, dettes). */
public final class CollectionsModuleScreen extends SectionedScreen {

    @Inject
    public CollectionsModuleScreen(Injector injector, Messages messages) {
        super(injector, messages);
    }

    @Override
    protected String titleKey() {
        return "nav.collections";
    }

    @Override
    protected Layout layout() {
        return Layout.TABS;
    }

    @Override
    protected List<Section> sections() {
        return List.of(
                new Section(null, "collections.tab.list", AppIcon.COLLECTIONS, CollectionsScreen.class),
                new Section(null, "collections.tab.accounts", AppIcon.LEDGER, DriverAccountsScreen.class));
    }
}
