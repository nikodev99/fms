package org.gu.fms.desktop.app.shell;

import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.app.AppPaths;
import org.gu.fms.desktop.app.AppRoutes;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.nav.Navigator;
import org.gu.fms.desktop.platform.ui.nav.Route;
import org.gu.fms.desktop.platform.ui.nav.RouteRegistry;
import org.gu.fms.desktop.platform.ui.theme.ThemeManager;
import org.gu.fms.desktop.platform.ui.theme.ThemeMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ShellController {
    private static final String LOGO = "org/gu/fms/desktop/images/fms-logo-128.png";

    private final Navigator navigator;
    private final RouteRegistry routes;
    private final ThemeManager theme;
    private final Messages messages;
    private final UserSession session;
    private final AppPaths paths;
    private final Map<Route, Button> navButtons = new HashMap<>();

    @FXML private VBox navBox;
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
    public ShellController(Navigator navigator, RouteRegistry routes, ThemeManager theme, Messages messages, UserSession session, AppPaths paths) {
        this.navigator = navigator;
        this.routes = routes;
        this.theme = theme;
        this.messages = messages;
        this.session = session;
        this.paths = paths;
    }

    @FXML
    private void initialize() {
        navigator.attach(contentHost);
        buildNavigation();
        bindTheme();
        loadLogo();

        UserSession.User user = session.currentUser();
        userInitials.setText(user.initials());
        userName.setText(user.displayName());
        userRole.setText(messages.label(user.role()));
        settingsButton.setOnAction(e -> navigator.navigate(AppRoutes.SETTINGS));
        databaseLabel.setText(messages.get("shell.database", paths.databaseFile().getFileName()));

        navigator.currentProperty().addListener((obs, prev, current) -> onRouteChanged(prev, current));
    }

    private void onRouteChanged(Route prev, Route current) {
        if (prev != null && navButtons.containsKey(prev)) {
            navButtons.get(prev).getStyleClass().remove("active");
        }
        if (current == null) return;

        Button b = navButtons.get(current);
        if (b != null && !b.getStyleClass().contains("active")) {
            b.getStyleClass().remove("active");
        }
        boolean footer = Route.FOOTER.equals(current.sectionKey());
        breadcrumbSection.setText(footer ? "" : messages.get(current.sectionKey()));
        breadcrumbSection.setManaged(!footer);
        breadcrumbSection.setVisible(!footer);
        breadcrumbPage.setText(messages.get(current.sectionKey()));
    }

    private void buildNavigation() {
        for (Map.Entry<String, List<Route>> section : routes.sections().entrySet()) {
            Label title = new Label(messages.get(section.getKey()).toUpperCase(messages.bundle().getLocale()));
            title.getStyleClass().add("nav-section");
            navBox.getChildren().add(title);
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
