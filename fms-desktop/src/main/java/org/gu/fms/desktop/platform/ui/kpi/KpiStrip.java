package org.gu.fms.desktop.platform.ui.kpi;

import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.gu.fms.desktop.platform.ui.Tone;

/**
 * Bandeau de chiffres clés (même style que le tableau de bord) : colonnes de largeur égale.
 *
 * <pre>{@code
 * KpiStrip strip = new KpiStrip();
 * KpiStrip.Kpi total = strip.add("Trésorerie consolidée");
 * total.set("1 250 000 XAF", "3 comptes", Tone.NEUTRAL);
 * }</pre>
 */
public final class KpiStrip extends GridPane {

    public KpiStrip() {
        getStyleClass().add("kpi-strip");
        setMinHeight(Region.USE_PREF_SIZE);
    }

    public Kpi add(String label) {
        int index = getColumnConstraints().size();
        ColumnConstraints c = new ColumnConstraints();
        c.setHgrow(Priority.ALWAYS);
        c.setFillWidth(true);
        getColumnConstraints().add(c);
        getColumnConstraints().forEach(cc -> cc.setPercentWidth(100.0 / getColumnConstraints().size()));
        Kpi kpi = new Kpi(label, index == 0);
        add(kpi.box, index, 0);
        return kpi;
    }

    /** Un indicateur : libellé, valeur, légende. */
    public static final class Kpi {
        private final VBox box;
        private final Label value = new Label("—");
        private final Label caption = new Label();

        private Kpi(String label, boolean first) {
            Label l = new Label(label);
            l.getStyleClass().add("kpi-label");
            value.getStyleClass().add("kpi-value");
            caption.getStyleClass().add("kpi-caption");
            caption.managedProperty().bind(caption.textProperty().isNotEmpty());
            caption.visibleProperty().bind(caption.managedProperty());
            caption.setWrapText(true);
            box = new VBox(l, value, caption);
            box.getStyleClass().add("kpi");
            if (first) {
                box.getStyleClass().add("first");
            }
        }

        /** Indicateur cliquable (ex. ouvre le module concerné). */
        public Kpi onClick(Runnable action) {
            box.getStyleClass().add("clickable");
            box.setOnMouseClicked(e -> action.run());
            return this;
        }

        public void set(String valueText, String captionText, Tone tone) {
            value.setText(valueText);
            caption.setText(captionText == null ? "" : captionText);
            value.getStyleClass().removeIf(s -> s.startsWith("tone-"));
            if (tone != null && tone != Tone.NEUTRAL) {
                value.getStyleClass().add("tone-" + tone.styleClass());
            }
        }
    }
}
