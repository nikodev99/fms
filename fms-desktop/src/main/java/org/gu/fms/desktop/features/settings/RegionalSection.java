package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.app.shell.SidebarPreferences;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.form.ChoiceInput;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.section.FormSection;
import org.gu.fms.desktop.platform.ui.theme.ThemeManager;
import org.gu.fms.desktop.platform.ui.theme.ThemeMode;
import org.gu.fms.domain.money.CurrencyUnit;

import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Paramètres › Général › Région et affichage.
 * Région (base de données, au prochain démarrage) ; affichage (préférences Windows, immédiat).
 */
public final class RegionalSection extends FormSection<RegionalService.Regional> {

    private final RegionalService service;
    private final ThemeManager theme;
    private final SidebarPreferences sidebar;
    private ChoiceInput<String> country;
    private ChoiceInput<CurrencyUnit> currency;
    private ChoiceInput<ZoneId> zone;
    private volatile boolean currencyLocked;
    private volatile boolean restartNeeded;

    @Inject
    public RegionalSection(CrudContext ui, RegionalService service, ThemeManager theme, SidebarPreferences sidebar) {
        super(ui);
        this.service = service;
        this.theme = theme;
        this.sidebar = sidebar;
    }

    @Override
    protected String title() {
        return messages.get("settings.regional");
    }

    @Override
    protected String description() {
        return messages.get("regional.description");
    }

    @Override
    protected Form buildForm() {
        Form form = ui.forms().create();
        form.section(messages.get("regional.section.region"));
        country = form.choice("country", messages.get("regional.country"), RegionalService.COUNTRIES,
                code -> new Locale("fr", code).getDisplayCountry(Locale.FRANCE)).required();
        currency = form.choice("currency", messages.get("regional.currency"), RegionalService.CURRENCIES,
                c -> messages.get("regional.currency." + c.code())).required();
        List<ZoneId> zones = ZoneId.getAvailableZoneIds().stream().filter(z -> z.startsWith("Africa/")).sorted()
                .map(ZoneId::of).toList();
        zone = form.choice("zone", messages.get("regional.zone"), zones, z -> z.getId().replace("Africa/", "")
                .replace('_', ' ')).required().help(messages.get("regional.zone.help"));
        return form;
    }

    @Override
    protected Node extra() {
        Label title = new Label(messages.get("regional.section.display"));
        title.getStyleClass().add("form-section-title");

        Label themeLabel = new Label(messages.get("regional.theme"));
        themeLabel.getStyleClass().add("field-label");
        ToggleGroup group = new ToggleGroup();
        HBox segmented = new HBox();
        segmented.getStyleClass().add("segmented");
        segmented.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        for (ThemeMode mode : ThemeMode.values()) {
            ToggleButton b = new ToggleButton(messages.label(mode), new FmsIcon(switch (mode) {
                case LIGHT -> AppIcon.THEME_LIGHT;
                case DARK -> AppIcon.THEME_DARK;
                case SYSTEM -> AppIcon.THEME_SYSTEM;
            }, 15));
            b.setToggleGroup(group);
            b.setUserData(mode);
            b.setSelected(theme.getMode() == mode);
            segmented.getChildren().add(b);
        }
        theme.modeProperty().addListener((obs, o, mode) -> group.getToggles().forEach(t ->
                t.setSelected(t.getUserData() == mode)));
        group.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                group.selectToggle(previous);
            } else {
                theme.setMode((ThemeMode) selected.getUserData());
            }
        });
        VBox themeBox = new VBox(6, themeLabel, segmented);

        CheckBox auto = new CheckBox(messages.get("regional.sidebarAuto"));
        auto.selectedProperty().bindBidirectional(sidebar.autoCollapseProperty());
        Label autoHelp = new Label(messages.get("regional.sidebarAuto.help"));
        autoHelp.getStyleClass().add("field-message");
        autoHelp.setWrapText(true);
        autoHelp.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        Label immediate = new Label(messages.get("regional.display.immediate"));
        immediate.getStyleClass().add("field-message");

        VBox box = new VBox(12, title, themeBox, new VBox(4, auto, autoHelp), immediate);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    @Override
    protected RegionalService.Regional load() {
        currencyLocked = service.currencyLocked();
        return service.load();
    }

    @Override
    protected void fill(RegionalService.Regional r) {
        country.setValue(r.country());
        currency.setValue(r.currency());
        zone.setValue(r.zone());
        currency.readOnly(currencyLocked);
        currency.help(messages.get(currencyLocked ? "regional.currency.lockedHelp" : "regional.currency.help"));
    }

    @Override
    protected RegionalService.Regional collect() {
        return new RegionalService.Regional(country.getValue(), currency.getValue(), zone.getValue());
    }

    @Override
    protected void save(RegionalService.Regional r) {
        restartNeeded = service.save(r);
    }

    @Override
    protected String savedMessage() {
        return messages.get(restartNeeded ? "regional.savedRestart" : "regional.saved");
    }
}
