package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.form.CheckInput;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.form.PercentInput;
import org.gu.fms.desktop.platform.ui.section.FormSection;
import org.gu.fms.domain.payroll.RecoveryPolicy;

/**
 * Paramètres › Paie › Retenues sur salaire : ce que la paie propose de retenir. Rappel du Code du travail
 * (art. 100) affiché : le logiciel propose, le propriétaire décide et peut diminuer chaque retenue.
 */
public final class RecoverySection extends FormSection<RecoveryPolicy> {

    private final RecoveryPolicyService service;
    private CheckInput advances;
    private CheckInput shortages;
    private CheckInput other;
    private PercentInput cap;

    @Inject
    public RecoverySection(CrudContext ui, RecoveryPolicyService service) {
        super(ui);
        this.service = service;
    }

    @Override
    protected String title() {
        return messages.get("settings.recoveries");
    }

    @Override
    protected String description() {
        return messages.get("recoveries.description");
    }

    @Override
    protected Form buildForm() {
        Form form = ui.forms().create();
        advances = form.check("recoverAdvances", messages.get("recoveries.advances"))
                .help(messages.get("recoveries.advances.help")).span(2);
        shortages = form.check("recoverShortages", messages.get("recoveries.shortages")).span(2);
        other = form.check("recoverOtherDebts", messages.get("recoveries.other")).span(2);
        cap = form.percent("debtCap", messages.get("recoveries.cap")).required()
                .help(messages.get("recoveries.cap.help"));
        return form;
    }

    @Override
    protected RecoveryPolicy load() {
        return service.load();
    }

    @Override
    protected void fill(RecoveryPolicy p) {
        advances.setValue(p.recoverAdvances());
        shortages.setValue(p.recoverShortages());
        other.setValue(p.recoverOtherDebts());
        cap.setValue(p.debtCap());
    }

    @Override
    protected RecoveryPolicy collect() {
        return new RecoveryPolicy(advances.getValue(), shortages.getValue(), other.getValue(), cap.getValue());
    }

    @Override
    protected void save(RecoveryPolicy p) {
        service.save(p);
    }

    @Override
    protected String savedMessage() {
        return messages.get("recoveries.saved");
    }
}
