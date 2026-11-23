package org.gu.fms.desktop.features.payroll;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.section.SectionedScreen;

import java.util.List;

/** Module Paie : bulletins mensuels | avances et acomptes. */
public final class PayrollModuleScreen extends SectionedScreen {

    @Inject
    public PayrollModuleScreen(Injector injector, Messages messages) {
        super(injector, messages);
    }

    @Override
    protected String titleKey() {
        return "nav.payroll";
    }

    @Override
    protected Layout layout() {
        return Layout.TABS;
    }

    @Override
    protected List<Section> sections() {
        return List.of(
                new Section(null, "payroll.tab.payslips", AppIcon.PAYROLL, PayslipsScreen.class),
                new Section(null, "payroll.tab.advances", AppIcon.ADVANCE, AdvancesScreen.class));
    }
}
