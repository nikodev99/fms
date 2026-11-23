package org.gu.fms.desktop.features.finance;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.section.SectionedScreen;

import java.util.List;

/** Module Transactions : grand livre | catégories. */
public final class TransactionsModuleScreen extends SectionedScreen {

    @Inject
    public TransactionsModuleScreen(Injector injector, Messages messages) {
        super(injector, messages);
    }

    @Override
    protected String titleKey() {
        return "nav.transactions";
    }

    @Override
    protected Layout layout() {
        return Layout.TABS;
    }

    @Override
    protected List<Section> sections() {
        return List.of(
                new Section(null, "transactions.tab.ledger", AppIcon.TRANSACTIONS, TransactionsScreen.class),
                new Section(null, "transactions.tab.categories", AppIcon.CATEGORY, CategoriesScreen.class));
    }
}