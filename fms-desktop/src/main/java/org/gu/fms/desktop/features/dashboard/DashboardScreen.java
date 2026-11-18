package org.gu.fms.desktop.features.dashboard;

import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.gu.fms.desktop.app.AppRoutes;
import org.gu.fms.desktop.features.vehicle.VehiculeService;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.ui.nav.Navigator;
import org.gu.fms.desktop.platform.ui.nav.Screen;
import org.gu.fms.desktop.platform.ui.task.UiTasks;
import org.gu.fms.desktop.platform.ui.view.ViewLoader;
import org.gu.fms.domain.vehicle.Vehicle;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class DashboardScreen implements Screen {
    private static final String VIEW = "/org/gu/fms/desktop/features/dashboard/dashboard.fxml";

    private final ViewLoader views;
    private final Navigator navigator;
    private final VehiculeService vehicles;
    private final UiTasks tasks;
    private final Messages messages;
    private final Formats formats;
    private final Clock clock;
    private final AppSettings settings;
    private Node view;

    @FXML private Label dateLabel;
    @FXML private Label activeVehicles;
    @FXML private Label vehiclesCaption;

    @Inject
    public DashboardScreen(ViewLoader views, Navigator navigator, VehiculeService vehiculeService, UiTasks tasks, Messages messages, Formats formats, Clock clock, AppSettings settings) {
        this.views = views;
        this.navigator = navigator;
        this.vehicles = vehiculeService;
        this.tasks = tasks;
        this.messages = messages;
        this.formats = formats;
        this.clock = clock;
        this.settings = settings;
    }

    @Override public Node view() {
        if(view == null)
            view = views.load(VIEW, this);

        return view;
    }

    @Override public void onShow() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), settings.zone());
        String text = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", settings.locale()));
        dateLabel.setText(Character.toUpperCase(text.charAt(0)) + text.substring(1));

        tasks.job(vehicles::findAll).onSuccess(this::showVehicleCounts).start();
    }

    @FXML
    private void openCash() {
        navigator.navigate(AppRoutes.CASH);
    }

    @FXML
    private void recordCollection() {
        navigator.navigate(AppRoutes.COLLECTIONS);
    }

    @FXML
    private void openVehicles() {
        navigator.navigate(AppRoutes.VEHICLES);
    }

    private void showVehicleCounts(List<Vehicle> all) {
        long active = all.stream().filter(v -> v.status().isOperational()).count();
        activeVehicles.setText(formats.number(active));
        vehiclesCaption.setText(messages.get("dashboard.kpi.vehicles.caption", all.size()));
    }

}
