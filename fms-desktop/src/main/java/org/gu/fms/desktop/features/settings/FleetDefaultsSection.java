package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.section.FormSection;
import org.gu.fms.domain.fleet.FleetPreferences;

/** Paramètres › Flotte › Valeurs par défaut et alertes. */
public final class FleetDefaultsSection extends FormSection<FleetPreferences> {

    private final FleetPreferencesService service;
    private MoneyInput threshold;
    private PercentInput warning;
    private NumberInput docDays;
    private NumberInput maintDays;
    private NumberInput maintKm;

    @Inject
    public FleetDefaultsSection(CrudContext ui, FleetPreferencesService service) {
        super(ui);
        this.service = service;
    }

    @Override
    protected String title() {
        return messages.get("settings.fleetDefaults");
    }

    @Override
    protected String description() {
        return messages.get("fleetDefaults.description");
    }

    @Override
    protected Form buildForm() {
        Form form = ui.forms().create();
        form.section(messages.get("fleetDefaults.section.repairs"));
        threshold = form.money("defaultRepairThreshold", messages.get("fleetDefaults.threshold"))
                .help(messages.get("fleetDefaults.threshold.help"));
        warning = form.percent("repairWarningPercent", messages.get("fleetDefaults.warning")).required()
                .help(messages.get("fleetDefaults.warning.help"));
        form.section(messages.get("fleetDefaults.section.alerts"));
        docDays = form.number("documentAlertDays", messages.get("fleetDefaults.docDays")).required()
                .unit(messages.get("unit.days")).rule(Rules.range(0, FleetPreferences.DAYS_MAX))
                .help(messages.get("fleetDefaults.docDays.help"));
        maintDays = form.number("maintenanceAlertDays", messages.get("fleetDefaults.maintDays")).required()
                .unit(messages.get("unit.days")).rule(Rules.range(0, FleetPreferences.DAYS_MAX));
        maintKm = form.number("maintenanceAlertKm", messages.get("fleetDefaults.maintKm")).required()
                .unit(messages.get("unit.km")).rule(Rules.range(0, FleetPreferences.KM_MAX))
                .help(messages.get("fleetDefaults.maint.help"));
        return form;
    }

    @Override
    protected FleetPreferences load() {
        return service.load();
    }

    @Override
    protected void fill(FleetPreferences p) {
        threshold.setValue(p.defaultRepairThreshold());
        warning.setValue(p.repairWarningPercent());
        docDays.setValue(p.documentAlertDays());
        maintDays.setValue(p.maintenanceAlertDays());
        maintKm.setValue(p.maintenanceAlertKm());
    }

    @Override
    protected FleetPreferences collect() {
        return new FleetPreferences(threshold.getValue(), warning.getValue(), docDays.getValue(), maintDays.getValue(),
                maintKm.getValue());
    }

    @Override
    protected void save(FleetPreferences p) {
        service.save(p);
    }

    @Override
    protected String savedMessage() {
        return messages.get("fleetDefaults.saved");
    }
}
