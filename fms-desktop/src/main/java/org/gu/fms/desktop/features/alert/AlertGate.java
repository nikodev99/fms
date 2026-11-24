package org.gu.fms.desktop.features.alert;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.app.AppRoutes;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.driver.DriverService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.features.vehicle.VehicleService;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.nav.Navigator;
import org.gu.fms.desktop.platform.ui.nav.Route;
import org.gu.fms.desktop.platform.ui.overlay.OverlayHost;
import org.gu.fms.desktop.platform.ui.overlay.Toast;
import org.gu.fms.desktop.platform.ui.task.UiTasks;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.vehicle.Vehicle;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alertes critiques bloquantes. Tant qu'une alerte critique d'un module ouvert à l'utilisateur subsiste, un dialogue
 * qu'on ne peut pas fermer s'affiche à la connexion et à chaque changement d'écran. Deux issues : « Mettre à jour »
 * (ouvre le module concerné, seul écran alors accessible avec la fiche du véhicule / chauffeur) ou « Mettre hors
 * service » (véhicule immobilisé, chauffeur suspendu). Un utilisateur sans accès au module concerné n'est pas bloqué.
 */
@Singleton
public final class AlertGate {

    private final AlertCenter center;
    private final Navigator navigator;
    private final OverlayHost overlays;
    private final UserSession session;
    private final UiTasks tasks;
    private final Toast toasts;
    private final Messages messages;
    private final VehicleRepository vehicles;
    private final VehicleService vehicleService;
    private final DriverRepository drivers;
    private final DriverService driverService;
    private StackPane layer;
    private boolean installed;

    @Inject
    public AlertGate(AlertCenter center, Navigator navigator, OverlayHost overlays, UserSession session, UiTasks tasks,
                     Toast toasts, Messages messages, VehicleRepository vehicles, VehicleService vehicleService,
                     DriverRepository drivers, DriverService driverService) {
        this.center = center;
        this.navigator = navigator;
        this.overlays = overlays;
        this.session = session;
        this.tasks = tasks;
        this.toasts = toasts;
        this.messages = messages;
        this.vehicles = vehicles;
        this.vehicleService = vehicleService;
        this.drivers = drivers;
        this.driverService = driverService;
    }

    /** Branche le contrôle sur la navigation (une fois). */
    public void install() {
        if (installed) {
            return;
        }
        installed = true;
        navigator.addInterceptor(this::allow);
        navigator.currentProperty().addListener((obs, o, r) -> center.refresh());
    }

    /** À la connexion : recalcule et bloque si nécessaire. */
    public void checkNow() {
        center.refresh(list -> {
            if (!list.stream().filter(AppAlert::critical).toList().isEmpty() && !onFixRoute(navigator.getCurrent())) {
                show();
            }
        });
    }

    /** Écrans qui permettent de régler les alertes critiques en cours. */
    private Set<String> fixRoutes() {
        Set<String> routes = center.blocking().stream().map(AppAlert::route).collect(Collectors.toSet());
        if (!routes.isEmpty()) {
            routes.add(AppRoutes.VEHICLES);
            routes.add(AppRoutes.DRIVERS);
        }
        return routes;
    }

    private boolean onFixRoute(Route r) {
        return r != null && fixRoutes().contains(r.id());
    }

    private boolean allow(Route target) {
        if (center.blocking().isEmpty() || fixRoutes().contains(target.id())) {
            return true;
        }
        // Les alertes ont peut-être été réglées : recalcul, puis navigation si plus rien ne bloque.
        center.refresh(list -> {
            if (list.stream().noneMatch(AppAlert::critical)) {
                navigator.navigate(target);
            } else {
                show();
            }
        });
        return false;
    }

    private void show() {
        List<AppAlert> blocking = center.blocking();
        if (blocking.isEmpty() || (layer != null && layer.getParent() != null)) {
            return;
        }
        Label title = new Label(messages.get("alert.blocking.title", blocking.size()));
        title.getStyleClass().add("dialog-title");
        title.setWrapText(true);
        Label text = new Label(messages.get("alert.blocking.text"));
        text.getStyleClass().add("dialog-message");
        text.setWrapText(true);
        FmsIcon icon = new FmsIcon(AppIcon.ERROR, 22);
        icon.getStyleClass().add("dialog-icon");
        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().addAll("dialog-icon-box", Tone.DANGER.styleClass());
        HBox top = new HBox(14, iconBox, new VBox(6, title, text));

        VBox rows = new VBox(8);
        for (AppAlert a : blocking) {
            rows.getChildren().add(row(a));
        }
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(360);
        scroll.getStyleClass().add("alert-scroll");

        VBox card = new VBox(18, top, scroll);
        card.getStyleClass().addAll("dialog", "alert-dialog");
        card.setMaxWidth(640);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        Region scrim = new Region();
        scrim.getStyleClass().add("scrim");
        layer = new StackPane(scrim, card);
        layer.getStyleClass().addAll("overlay-layer", "dialog-layer", "blocking-layer");
        layer.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                e.consume();                                    // ne se ferme pas
            }
        });
        overlays.push(layer);
    }

    private VBox row(AppAlert a) {
        Label t = new Label(a.title());
        t.getStyleClass().add("alert-row-title");
        t.setWrapText(true);
        Label d = new Label(a.detail());
        d.getStyleClass().add("alert-row-detail");
        d.setWrapText(true);
        Button update = new Button(messages.get("alert.update"), new FmsIcon(AppIcon.EDIT, 16));
        update.getStyleClass().add("primary");
        update.setOnAction(e -> {
            close();
            navigator.navigate(a.route());
        });
        HBox actions = new HBox(8, update);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (a.vehicleId() != null && session.canAccess(AppRoutes.VEHICLES)) {
            Button off = new Button(messages.get("alert.outOfService"), new FmsIcon(AppIcon.OUT_OF_SERVICE, 16));
            off.getStyleClass().add("danger");
            off.setOnAction(e -> outOfService(a));
            actions.getChildren().add(0, off);
        } else if (a.driverId() != null && session.canAccess(AppRoutes.DRIVERS)) {
            Button off = new Button(messages.get("alert.suspendDriver"), new FmsIcon(AppIcon.OUT_OF_SERVICE, 16));
            off.getStyleClass().add("danger");
            off.setOnAction(e -> outOfService(a));
            actions.getChildren().add(0, off);
        }
        VBox texts = new VBox(2, t, d);
        HBox.setHgrow(texts, Priority.ALWAYS);
        VBox box = new VBox(8, texts, actions);
        box.getStyleClass().add("alert-row");
        return box;
    }

    /** Véhicule immobilisé (document) ou en entretien (entretien en retard) ; chauffeur suspendu. */
    private void outOfService(AppAlert a) {
        tasks.job(() -> {
            if (a.vehicleId() != null) {
                Vehicle.VehicleStatus status = a.kind() == AppAlert.Kind.MAINTENANCE
                        ? Vehicle.VehicleStatus.UNDER_MAINTENANCE : Vehicle.VehicleStatus.IMMOBILISED;
                var v = vehicles.findById(a.vehicleId()).orElseThrow();
                vehicleService.save(v.withStatus(status));
                return v.registration();
            }
            var d = drivers.findById(a.driverId()).orElseThrow();
            driverService.save(d.withStatus(Driver.DriverStatus.SUSPENDED));
            return d.displayName();
        }).onSuccess(name -> {
            toasts.success(messages.get("alert.outOfService.done", name));
            close();
            center.refresh(list -> {
                if (list.stream().anyMatch(AppAlert::critical)) {
                    show();
                }
            });
        }).start();
    }

    private void close() {
        if (layer != null) {
            overlays.remove(layer);
        }
    }

    /** Déconnexion : rien ne doit rester affiché. */
    public void reset() {
        close();
    }
}
