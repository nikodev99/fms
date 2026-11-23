package org.gu.fms.desktop.features.finance;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.section.SectionedScreen;

import java.util.List;

/** Module Comptes : comptes de trésorerie | virements internes. */
public final class AccountsModuleScreen extends SectionedScreen {

    @Inject
    public AccountsModuleScreen(Injector injector, Messages messages) {
        super(injector, messages);
    }

    @Override
    protected String titleKey() {
        return "nav.accounts";
    }

    @Override
    protected Layout layout() {
        return Layout.TABS;
    }

    @Override
    protected List<Section> sections() {
        return List.of(
                new Section(null, "accounts.tab.list", AppIcon.ACCOUNTS, AccountsScreen.class),
                new Section(null, "accounts.tab.transfers", AppIcon.TRANSFER, TransfersScreen.class));
    }
}
