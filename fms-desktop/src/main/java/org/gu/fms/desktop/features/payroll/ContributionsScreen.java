package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.payroll.ContributionGroup;
import org.gu.fms.domain.payroll.ContributionPayer;
import org.gu.fms.domain.payroll.PayrollContribution;
import org.gu.fms.domain.payroll.PayrollContributionRules;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Paramètres › Paie › Cotisations. Une ligne par taux daté : la colonne « En vigueur » montre celle que la paie
 * appliquerait aujourd'hui. « À confirmer » signale un taux dont la source n'est pas un texte officiel vérifié.
 */
public final class ContributionsScreen extends CrudScreen<PayrollContribution> {

    private final PayrollContributionService service;
    private final Clock clock;
    private final AppSettings settings;
    private volatile Set<Long> inForce = Set.of();

    @Inject
    public ContributionsScreen(CrudContext ui, PayrollContributionService service, Clock clock, AppSettings settings) {
        super(ui, service);
        this.service = service;
        this.clock = clock;
        this.settings = settings;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    @Override
    protected void prepare() {
        List<PayrollContribution> all = service.findAll();
        Set<Long> ids = new HashSet<>();
        all.stream().map(PayrollContribution::code).distinct().forEach(code ->
                PayrollContributionRules.inForce(all, code, today()).ifPresent(c -> ids.add(c.id())));
        inForce = ids;
    }

    @Override
    protected String title() {
        return messages.get("settings.contributions");
    }

    @Override
    protected String countText(int visible, int total) {
        return messages.get("contributions.count", total);
    }

    @Override
    protected String hint() {
        return messages.get("contributions.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("contribution.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.CONTRIBUTIONS;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("contributions.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("contributions.empty.text");
    }

    @Override
    protected void configureTable(DataTable<PayrollContribution> table) {
        table.textColumn(messages.get("contribution.name"), PayrollContribution::name, 250).setMinWidth(170);
        table.textColumn(messages.get("contribution.payer.short"), c -> messages.label(c.payer()), 95).setMinWidth(90);
        table.textColumn(messages.get("contribution.rate"), c -> ui.formats().percent(c.rate()), 70)
                .getStyleClass().add("numeric");
        table.moneyColumn(messages.get("contribution.ceiling.short"), PayrollContribution::monthlyCeiling, 100);
        table.dateColumn(messages.get("contribution.effectiveFrom"), PayrollContribution::effectiveFrom, 100)
                .setMinWidth(95);
        table.badgeColumn(messages.get("contribution.status"), c -> !c.enabled()
                ? new Badge(messages.get("contribution.notApplied"), Tone.NEUTRAL)
                : inForce.contains(c.id()) ? new Badge(messages.get("contribution.inForce"), Tone.SUCCESS)
                : new Badge(messages.get("contribution.notInForce"), Tone.NEUTRAL), 130).setMinWidth(125);
        table.badgeColumn(messages.get("contribution.verified"), c -> c.verified()
                ? new Badge(messages.get("contribution.verified.yes"), Tone.SUCCESS)
                : new Badge(messages.get("contribution.verified.no"), Tone.WARNING), 120).setMinWidth(115);
        table.searchBy(c -> String.join(" ", c.code(), c.name(), messages.label(c.group()), c.legalSource()));
    }

    @Override
    protected EntityEditor<PayrollContribution> createEditor(PayrollContribution existing) {
        return new Editor(ui.forms(), messages, existing);
    }

    @Override
    protected String savedMessage(PayrollContribution saved, boolean created) {
        return messages.get(created ? "contribution.created" : "contribution.updated", saved.name());
    }

    @Override
    protected Object identityOf(PayrollContribution c) {
        return c.id();
    }

    private static final class Editor implements EntityEditor<PayrollContribution> {
        private final Messages messages;
        private final PayrollContribution existing;
        private final Form form;
        private final TextInput code;
        private final TextInput name;
        private final ChoiceInput<ContributionGroup> group;
        private final ChoiceInput<ContributionPayer> payer;
        private final PercentInput rate;
        private final MoneyInput ceiling;
        private final MoneyInput floor;
        private final DateInput effectiveFrom;
        private final CheckInput enabled;
        private final TextAreaInput source;
        private final CheckInput verified;

        Editor(Forms forms, Messages messages, PayrollContribution existing) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            form.section(messages.get("contribution.section.identity"));
            name = form.text("name", messages.get("contribution.name")).required()
                    .maxLength(PayrollContributionRules.NAME_MAX).span(2);
            code = form.text("code", messages.get("contribution.code")).required()
                    .maxLength(PayrollContributionRules.CODE_MAX).help(messages.get("contribution.code.help"));
            group = form.choice("group", messages.get("contribution.group"), ContributionGroup.class).required()
                    .help(messages.get("contribution.group.help"));
            payer = form.choice("payer", messages.get("contribution.payer"), ContributionPayer.class).required();
            enabled = form.check("enabled", messages.get("contribution.enabled"))
                    .help(messages.get("contribution.enabled.help"));
            form.section(messages.get("contribution.section.rate"));
            rate = form.percent("rate", messages.get("contribution.rate")).required();
            effectiveFrom = form.date("effectiveFrom", messages.get("contribution.effectiveFrom")).required()
                    .help(messages.get("contribution.effectiveFrom.help"));
            ceiling = form.money("monthlyCeiling", messages.get("contribution.ceiling"))
                    .help(messages.get("contribution.ceiling.help"));
            floor = form.money("monthlyFloor", messages.get("contribution.floor"))
                    .help(messages.get("contribution.floor.help"));
            form.section(messages.get("contribution.section.source"));
            source = form.textArea("legalSource", messages.get("contribution.source")).required()
                    .maxLength(PayrollContributionRules.SOURCE_MAX);
            verified = form.check("verified", messages.get("contribution.verified.check"))
                    .help(messages.get("contribution.verified.help"));
            if (existing == null) {
                group.setValue(ContributionGroup.OTHER);
                payer.setValue(ContributionPayer.EMPLOYER);
                enabled.setValue(true);
            } else {
                code.setValue(existing.code()).readOnly(true);
                name.setValue(existing.name());
                group.setValue(existing.group());
                payer.setValue(existing.payer());
                rate.setValue(existing.rate());
                ceiling.setValue(existing.monthlyCeiling());
                floor.setValue(existing.monthlyFloor());
                effectiveFrom.setValue(existing.effectiveFrom());
                enabled.setValue(existing.enabled());
                source.setValue(existing.legalSource());
                verified.setValue(existing.verified());
            }
        }

        @Override
        public String title() {
            return existing == null ? messages.get("contribution.create") : existing.name();
        }

        @Override
        public String subtitle() {
            return existing == null ? messages.get("contribution.create.subtitle") : existing.code();
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public PayrollContribution toEntity() {
            return new PayrollContribution(existing == null ? null : existing.id(),
                    existing == null ? 0 : existing.version(), code.getValue(), name.getValue(), group.getValue(),
                    payer.getValue(), rate.getValue(), ceiling.getValue(), floor.getValue(), enabled.getValue(),
                    effectiveFrom.getValue(), source.getValue(), verified.getValue());
        }
    }
}
