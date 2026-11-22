package org.gu.fms.desktop.app.shell;

import jakarta.inject.Inject;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.gu.fms.desktop.app.AppPaths;
import org.gu.fms.desktop.app.AppRoutes;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.nav.Navigator;
import org.gu.fms.desktop.platform.ui.nav.Route;
import org.gu.fms.desktop.platform.ui.nav.RouteRegistry;
import org.gu.fms.desktop.platform.ui.theme.ThemeManager;
import org.gu.fms.desktop.platform.ui.theme.ThemeMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Coque de l'application : menu latéral (généré depuis {@link RouteRegistry}), barre supérieure,
 * zone de contenu et barre d'état.
 * <p>
 * Menu latéral réductible :
 *  - bouton ☰ de la barre supérieure : déplié (icônes + libellés) ↔ réduit (icônes seules, info-bulles) ;
 *  - automatique : sous {@value #NARROW_WIDTH} px de largeur de fenêtre le menu se réduit, au-dessus il se déplie
 *    (option désactivable dans Paramètres › Région et affichage). Seul le passage du seuil déclenche le changement :
 *    un choix manuel est respecté tant que la fenêtre reste du même côté du seuil.
 */
public final class ShellController {

    static final double NARROW_WIDTH = 1200;
    private static final double EXPANDED_WIDTH = 248;
    private static final double COLLAPSED_WIDTH = 68;
    private static final Duration ANIMATION = Duration.millis(160);
    private static final String LOGO = "/com/globalunderscore/fms/desktop/images/fms-logo-128.png";

    private final Navigator navigator;
    private final RouteRegistry routes;
    private final ThemeManager theme;
    private final Messages messages;
    private final UserSession session;
    private final AppPaths paths;
    private final SidebarPreferences sidebarPrefs;
    private final Map<Route, Button> navButtons = new HashMap<>();
    private final List<Label> sectionLabels = new ArrayList<>();
    private final List<Separator> sectionSeparators = new ArrayList<>();
    private final DoubleProperty sidebarWidth = new SimpleDoubleProperty(EXPANDED_WIDTH);
    private Timeline widthAnimation;
    private boolean collapsed;
    private Boolean narrow;

    @FXML private VBox sidebar;
    @FXML private HBox brand;
    @FXML private VBox brandText;
    @FXML private VBox navBox;
    @FXML private HBox sidebarFooter;
    @FXML private VBox userBox;
    @FXML private Button sidebarToggle;
    @FXML private FmsIcon sidebarToggleIcon;
    @FXML private StackPane contentHost;
    @FXML private ImageView logoImage;
    @FXML private Label logoFallback;
    @FXML private Label breadcrumbSection;
    @FXML private Label breadcrumbPage;
    @FXML private ToggleGroup themeGroup;
    @FXML private ToggleButton themeLight;
    @FXML private ToggleButton themeDark;
    @FXML private ToggleButton themeSystem;
    @FXML private Label userInitials;
    @FXML private Label userName;
    @FXML private Label userRole;
    @FXML private Button settingsButton;
    @FXML private Label databaseLabel;

    @Inject
    public ShellController(Navigator navigator, RouteRegistry routes, ThemeManager theme, Messages messages,
                           UserSession session, AppPaths paths, SidebarPreferences sidebarPrefs) {
        this.navigator = navigator;
        this.routes = routes;
        this.theme = theme;
        this.messages = messages;
        this.session = session;
        this.paths = paths;
        this.sidebarPrefs = sidebarPrefs;
    }

    @FXML
    private void initialize() {
        navigator.attach(contentHost);
        buildNavigation();
        bindTheme();
        loadLogo();
        bindSidebar();

        UserSession.User user = session.currentUser();
        userInitials.setText(user.initials());
        userName.setText(user.displayName());
        userRole.setText(messages.label(user.role()));
        settingsButton.setOnAction(e -> navigator.navigate(AppRoutes.SETTINGS));
        databaseLabel.setText(messages.get("shell.database", paths.databaseFile().getFileName()));

        navigator.currentProperty().addListener((obs, previous, current) -> onRouteChanged(previous, current));
    }

    private void buildNavigation() {
        for (Map.Entry<String, List<Route>> section : routes.sections().entrySet()) {
            Label title = new Label(messages.get(section.getKey()).toUpperCase(messages.bundle().getLocale()));
            title.getStyleClass().add("nav-section");
            Separator separator = new Separator();
            separator.getStyleClass().add("nav-separator");
            separator.setManaged(false);
            separator.setVisible(false);
            sectionLabels.add(title);
            sectionSeparators.add(separator);
            navBox.getChildren().addAll(title, separator);
            for (Route route : section.getValue()) {
                Button b = new Button(messages.get(route.titleKey()), new FmsIcon(route.icon()));
                b.getStyleClass().add("nav-item");
                b.setMaxWidth(Double.MAX_VALUE);
                b.setOnAction(e -> navigator.navigate(route));
                navButtons.put(route, b);
                navBox.getChildren().add(b);
            }
        }
    }

    // ---- Menu latéral réductible -------------------------------------------------------------

    private void bindSidebar() {
        sidebar.minWidthProperty().bind(sidebarWidth);
        sidebar.prefWidthProperty().bind(sidebarWidth);
        sidebar.maxWidthProperty().bind(sidebarWidth);
        sidebarToggle.setOnAction(e -> toggleSidebar());
        applyCollapsed(false, false);

        sidebar.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                watchWidth(scene);
            }
        });
        sidebarPrefs.autoCollapseProperty().addListener((obs, o, auto) -> {
            if (auto && Boolean.TRUE.equals(narrow) && !collapsed) {
                applyCollapsed(true, true);
            }
        });
    }

    private void watchWidth(Scene scene) {
        scene.widthProperty().addListener((obs, o, w) -> onWidth(w.doubleValue()));
        onWidth(scene.getWidth());
    }

    /** Seul le franchissement du seuil agit, et seulement si l'option automatique est active. */
    private void onWidth(double width) {
        if (width <= 0) {
            return;
        }
        boolean nowNarrow = width < NARROW_WIDTH;
        boolean first = narrow == null;
        if (!first && nowNarrow == narrow) {
            return;
        }
        narrow = nowNarrow;
        boolean target = sidebarPrefs.autoCollapseProperty().get()
                ? nowNarrow || (first && sidebarPrefs.savedCollapsed())
                : first ? sidebarPrefs.savedCollapsed() : collapsed;
        if (target != collapsed) {
            applyCollapsed(target, !first);
        }
    }

    private void toggleSidebar() {
        applyCollapsed(!collapsed, true);
        if (!Boolean.TRUE.equals(narrow)) {        // choix mémorisé seulement pour une fenêtre large
            sidebarPrefs.saveCollapsed(collapsed);
        }
    }

    private void applyCollapsed(boolean value, boolean animate) {
        collapsed = value;
        if (value) {
            if (!sidebar.getStyleClass().contains("collapsed")) {
                sidebar.getStyleClass().add("collapsed");
            }
        } else {
            sidebar.getStyleClass().remove("collapsed");
        }
        for (Map.Entry<Route, Button> e : navButtons.entrySet()) {
            Button b = e.getValue();
            b.setContentDisplay(value ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
            b.setTooltip(value ? tooltip(messages.get(e.getKey().titleKey())) : null);
        }
        sectionLabels.forEach(l -> show(l, !value));
        sectionSeparators.forEach(s -> show(s, value));
        show(brandText, !value);
        show(userBox, !value);
        show(userInitials, !value);
        brand.setAlignment(value ? Pos.CENTER : Pos.CENTER_LEFT);
        sidebarFooter.setAlignment(value ? Pos.CENTER : Pos.CENTER_LEFT);
        sidebarToggleIcon.setIcon(value ? AppIcon.MENU : AppIcon.MENU_OPEN);
        sidebarToggle.setTooltip(tooltip(messages.get(value ? "shell.sidebar.expand" : "shell.sidebar.collapse")));

        double target = value ? COLLAPSED_WIDTH : EXPANDED_WIDTH;
        if (widthAnimation != null) {
            widthAnimation.stop();
        }
        if (animate) {
            widthAnimation = new Timeline(new KeyFrame(ANIMATION,
                    new KeyValue(sidebarWidth, target, Interpolator.EASE_BOTH)));
            widthAnimation.play();
        } else {
            sidebarWidth.set(target);
        }
    }

    /** Pour les tests d'interface. */
    boolean isSidebarCollapsed() {
        return collapsed;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static Tooltip tooltip(String text) {
        Tooltip t = new Tooltip(text);
        t.setShowDelay(Duration.millis(300));
        return t;
    }

    // ---- Navigation, thème, logo -------------------------------------------------------------

    private void onRouteChanged(Route previous, Route current) {
        if (previous != null && navButtons.containsKey(previous)) {
            navButtons.get(previous).getStyleClass().remove("active");
        }
        if (current == null) {
            return;
        }
        Button b = navButtons.get(current);
        if (b != null && !b.getStyleClass().contains("active")) {
            b.getStyleClass().add("active");
        }
        boolean footer = Route.FOOTER.equals(current.sectionKey());
        breadcrumbSection.setText(footer ? "" : messages.get(current.sectionKey()));
        breadcrumbSection.setManaged(!footer);
        breadcrumbSection.setVisible(!footer);
        breadcrumbPage.setText(messages.get(current.titleKey()));
    }

    private void bindTheme() {
        Map<ThemeMode, ToggleButton> buttons = Map.of(
                ThemeMode.LIGHT, themeLight,
                ThemeMode.DARK, themeDark,
                ThemeMode.SYSTEM, themeSystem);
        buttons.forEach((mode, button) -> button.setUserData(mode));
        themeGroup.selectToggle(buttons.get(theme.getMode()));
        themeGroup.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {               // un clic sur le bouton actif ne désélectionne pas tout
                themeGroup.selectToggle(previous);
                return;
            }
            theme.setMode((ThemeMode) selected.getUserData());
        });
        theme.modeProperty().addListener((obs, o, mode) -> {    // changé depuis Paramètres
            ToggleButton b = buttons.get(mode);
            if (b != null && !b.isSelected()) {
                themeGroup.selectToggle(b);
            }
        });
    }

    private void loadLogo() {
        try (InputStream in = ShellController.class.getResourceAsStream(LOGO)) {
            if (in == null) {
                return;
            }
            logoImage.setImage(new Image(in));
            logoImage.setVisible(true);
            logoImage.setManaged(true);
            logoFallback.setVisible(false);
            logoFallback.setManaged(false);
        } catch (IOException e) {
            // logo illisible : le monogramme reste affiché
        }
    }
}
