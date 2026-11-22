package org.gu.fms.desktop.features.payroll;

import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.payroll.ItsBand;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tranches du barème ITS (affiché dans la section « Impôt sur les salaires »). */
public final class ItsBandsScreen extends CrudScreen<ItsBand> {

    private final ItsBandService service;
    private volatile Map<Long, Money> lowerBounds = Map.of();

    @Inject
    public ItsBandsScreen(CrudContext ui, ItsBandService service) {
        super(ui, service);
        this.service = service;
    }

    /** Début de chaque tranche = limite de la précédente + 1 (même date d'effet). */
    @Override
    protected void prepare() {
        Map<Long, Money> lower = new HashMap<>();
        Map<java.time.LocalDate, List<ItsBand>> byDate = new HashMap<>();
        for (ItsBand b : service.findAll()) {
            byDate.computeIfAbsent(b.effectiveFrom(), d -> new java.util.ArrayList<>()).add(b);
        }
        for (List<ItsBand> list : byDate.values()) {
            list.sort(Comparator.comparing(ItsBand::upTo, Comparator.nullsLast(Comparator.naturalOrder())));
            Money previous = null;
            for (ItsBand b : list) {
                lower.put(b.id(), previous == null ? null : previous.plus(Money.of(1, previous.currency())));
                previous = b.upTo();
            }
        }
        lowerBounds = lower;
    }

    @Override
    protected String title() {
        return messages.get("its.bands.title");
    }

    @Override
    protected String countText(int visible, int total) {
        return messages.get("its.bands.count", total);
    }

    @Override
    protected String createLabel() {
        return messages.get("its.band.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.TAX;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("its.bands.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("its.bands.empty.text");
    }

    @Override
    protected void configureTable(DataTable<ItsBand> table) {
        table.dateColumn(messages.get("its.band.effectiveFrom"), ItsBand::effectiveFrom, 110);
        table.textColumn(messages.get("its.band.from"), b -> {
            Money lower = lowerBounds.get(b.id());
            return lower == null ? "0" : ui.formats().amount(lower);
        }, 120).getStyleClass().add("numeric");
        table.textColumn(messages.get("its.band.upTo"), b -> b.upTo() == null ? messages.get("its.band.noLimit")
                : ui.formats().amount(b.upTo()), 120).getStyleClass().add("numeric");
        table.textColumn(messages.get("its.band.tax"), b -> b.rate() != null ? ui.formats().percent(b.rate())
                : messages.get("its.band.fixed", ui.formats().money(b.fixedAmount())), 160);
        table.searchBy(b -> String.valueOf(b.effectiveFrom()));
    }

    @Override
    protected EntityEditor<ItsBand> createEditor(ItsBand existing) {
        return new Editor(ui.forms(), messages, existing);
    }

    @Override
    protected String savedMessage(ItsBand saved, boolean created) {
        return messages.get(created ? "its.band.created" : "its.band.updated");
    }

    @Override
    protected Object identityOf(ItsBand b) {
        return b.id();
    }

    private static final class Editor implements EntityEditor<ItsBand> {
        private final Messages messages;
        private final ItsBand existing;
        private final Form form;
        private final DateInput effectiveFrom;
        private final MoneyInput upTo;
        private final PercentInput rate;
        private final MoneyInput fixed;

        Editor(Forms forms, Messages messages, ItsBand existing) {
            this.messages = messages;
            this.existing = existing;
            form = forms.create();
            effectiveFrom = form.date("effectiveFrom", messages.get("its.band.effectiveFrom")).required();
            upTo = form.money("upTo", messages.get("its.band.upTo")).help(messages.get("its.band.upTo.help"));
            rate = form.percent("rate", messages.get("its.band.rate")).help(messages.get("its.band.rate.help"));
            fixed = form.money("fixedAmount", messages.get("its.band.fixedAmount"));
            if (existing != null) {
                effectiveFrom.setValue(existing.effectiveFrom());
                upTo.setValue(existing.upTo());
                rate.setValue(existing.rate());
                fixed.setValue(existing.fixedAmount());
            }
        }

        @Override
        public String title() {
            return messages.get(existing == null ? "its.band.create" : "its.band.edit");
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public ItsBand toEntity() {
            return new ItsBand(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    effectiveFrom.getValue(), upTo.getValue(), rate.getValue(), fixed.getValue());
        }
    }
}
