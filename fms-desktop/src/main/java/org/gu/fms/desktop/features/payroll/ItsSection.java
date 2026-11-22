package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.section.FormSection;
import org.gu.fms.domain.payroll.ItsSettings;

/** Paramètres › Paie › Impôt sur les salaires (ITS) : options et barème. */
public final class ItsSection extends FormSection<ItsSettings> {

    private final ItsSettingsService service;
    private final ItsBandsScreen bands;
    private CheckInput enabled;
    private PercentInput abatement;
    private MoneyInput minimum;
    private TextAreaInput source;

    @Inject
    public ItsSection(CrudContext ui, ItsSettingsService service, ItsBandsScreen bands) {
        super(ui);
        this.service = service;
        this.bands = bands;
        bands.embed();
    }

    @Override
    protected String title() {
        return messages.get("settings.its");
    }

    @Override
    protected String description() {
        return messages.get("its.description");
    }

    @Override
    protected Form buildForm() {
        Form form = ui.forms().create();
        enabled = form.check("enabled", messages.get("its.enabled")).help(messages.get("its.enabled.help")).span(2);
        abatement = form.percent("abatement", messages.get("its.abatement")).required()
                .help(messages.get("its.abatement.help"));
        minimum = form.money("minimumTax", messages.get("its.minimumTax")).required()
                .help(messages.get("its.minimumTax.help"));
        source = form.textArea("legalSource", messages.get("its.source")).required().maxLength(ItsSettings.SOURCE_MAX)
                .rows(2);
        return form;
    }

    @Override
    protected Node extra() {
        VBox box = new VBox(bands.view());
        box.getStyleClass().add("embedded-table");
        return box;
    }

    @Override
    public void onShow() {
        super.onShow();
        bands.onShow();
    }

    @Override
    protected ItsSettings load() {
        return service.load();
    }

    @Override
    protected void fill(ItsSettings s) {
        enabled.setValue(s.enabled());
        abatement.setValue(s.abatement());
        minimum.setValue(s.minimumTax());
        source.setValue(s.legalSource());
    }

    @Override
    protected ItsSettings collect() {
        return new ItsSettings(enabled.getValue(), abatement.getValue(), minimum.getValue(), source.getValue());
    }

    @Override
    protected void save(ItsSettings s) {
        service.save(s);
    }

    @Override
    protected String savedMessage() {
        return messages.get("its.saved");
    }
}