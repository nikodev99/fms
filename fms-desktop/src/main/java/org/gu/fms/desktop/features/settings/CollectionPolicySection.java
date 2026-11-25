package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import org.gu.fms.desktop.features.collection.CollectionPolicyService;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.form.MultiChoiceInput;
import org.gu.fms.desktop.platform.ui.form.PercentInput;
import org.gu.fms.desktop.platform.ui.section.FormSection;
import org.gu.fms.domain.collection.CollectionPolicy;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.List;

/** Paramètres › Exploitation › Versements et recouvrement : jours de repos, objectif, seuil rouge. */
public final class CollectionPolicySection extends FormSection<CollectionPolicy> {

    private final CollectionPolicyService service;
    private MultiChoiceInput<DayOfWeek> restDays;
    private PercentInput objective;
    private PercentInput red;

    @Inject
    public CollectionPolicySection(CrudContext ui, CollectionPolicyService service) {
        super(ui);
        this.service = service;
    }

    @Override
    protected String title() {
        return messages.get("settings.collectionPolicy");
    }

    @Override
    protected String description() {
        return messages.get("collectionPolicy.description");
    }

    @Override
    protected Form buildForm() {
        Form form = ui.forms().create();
        form.section(messages.get("collectionPolicy.section.rest"));
        restDays = form.multi("restDays", messages.get("collectionPolicy.restDays"), List.of(DayOfWeek.values()),
                        d -> capitalize(d.getDisplayName(TextStyle.FULL, messages.locale()))).span(2)
                .help(messages.get("collectionPolicy.restDays.help"));
        form.section(messages.get("collectionPolicy.section.kpi"));
        objective = form.percent("objective", messages.get("collectionPolicy.objective")).required()
                .help(messages.get("collectionPolicy.objective.help"));
        red = form.percent("redThreshold", messages.get("collectionPolicy.red")).required()
                .help(messages.get("collectionPolicy.red.help"));
        return form;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected CollectionPolicy load() {
        return service.load();
    }

    @Override
    protected void fill(CollectionPolicy p) {
        restDays.setValue(List.copyOf(p.restDays()));
        objective.setValue(p.objective());
        red.setValue(p.redThreshold());
    }

    @Override
    protected CollectionPolicy collect() {
        List<DayOfWeek> days = restDays.getValue();
        return new CollectionPolicy(days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days),
                objective.getValue(), red.getValue());
    }

    @Override
    protected void save(CollectionPolicy p) {
        service.save(p);
    }

    @Override
    protected String savedMessage() {
        return messages.get("collectionPolicy.saved");
    }
}