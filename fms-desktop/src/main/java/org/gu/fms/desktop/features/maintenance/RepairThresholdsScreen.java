package org.gu.fms.desktop.features.maintenance;

import jakarta.inject.Inject;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.maintenance.RepairAssessment;

/**
 * Entretien › Seuils de réparation (lecture seule). Indicateur de gestion : un seuil atteint invite à évaluer
 * le véhicule, jamais à le vendre automatiquement (instructions §4.1). Le seuil se règle sur la fiche véhicule.
 */
public final class RepairThresholdsScreen extends CrudScreen<RepairRow> {

    @Inject
    public RepairThresholdsScreen(CrudContext ui, RepairThresholdService service) {
        super(ui, service);
    }

    @Override
    protected String title() {
        return messages.get("maintenance.tab.thresholds");
    }

    @Override
    protected String countText(int visible, int total) {
        return messages.get("thresholds.count", total);
    }

    @Override
    protected String hint() {
        return messages.get("thresholds.hint");
    }

    @Override
    protected boolean canCreate() {
        return false;
    }

    @Override
    protected boolean canOpen(RepairRow item) {
        return false;
    }

    @Override
    protected String createLabel() {
        return "";
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.THRESHOLD;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("thresholds.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("thresholds.empty.text");
    }

    private Badge level(RepairRow r) {
        RepairAssessment a = r.assessment();
        return new Badge(messages.label(a.level()), switch (a.level()) {
            case REACHED -> Tone.DANGER;
            case APPROACHING -> Tone.WARNING;
            case OK -> Tone.SUCCESS;
            case NO_THRESHOLD -> Tone.NEUTRAL;
        });
    }

    @Override
    protected void configureTable(DataTable<RepairRow> table) {
        table.textColumn(messages.get("threshold.vehicle"), r -> r.vehicle().registration() + " — "
                + r.vehicle().make() + " " + r.vehicle().model(), 220).setMinWidth(150);
        table.numberColumn(messages.get("threshold.count"), RepairRow::repairCount, null, 100);
        table.moneyColumn(messages.get("threshold.total"), r -> r.assessment().total(), 140);
        table.moneyColumn(messages.get("threshold.threshold"), r -> r.assessment().threshold(), 140);
        table.textColumn(messages.get("threshold.used"), r -> r.assessment().usedPercent() == null ? "—"
                : r.assessment().usedPercent() + " %", 90).getStyleClass().add("numeric");
        table.badgeColumn(messages.get("threshold.level"), this::level, 150);
        table.searchBy(r -> r.vehicle().registration() + " " + r.vehicle().make() + " " + r.vehicle().model());
    }

    @Override
    protected EntityEditor<RepairRow> createEditor(RepairRow existing) {
        return null;
    }

    @Override
    protected String savedMessage(RepairRow saved, boolean created) {
        return "";
    }

    @Override
    protected Object identityOf(RepairRow r) {
        return r.vehicle().id();
    }
}
