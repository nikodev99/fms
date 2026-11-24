package org.gu.fms.desktop.features.alert;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.ui.task.UiTasks;

import java.util.List;
import java.util.function.Consumer;

/**
 * Alertes visibles par l'utilisateur connecté (celles des modules qui lui sont ouverts), rafraîchies en tâche de fond
 * à la connexion, à chaque changement d'écran et à la demande. Liste et compteurs sur le fil JavaFX.
 */
@Singleton
public final class AlertCenter {

    private final AlertService service;
    private final UserSession session;
    private final UiTasks tasks;
    private final ObservableList<AppAlert> alerts = FXCollections.observableArrayList();
    private final IntegerProperty criticalCount = new SimpleIntegerProperty();
    private final IntegerProperty totalCount = new SimpleIntegerProperty();

    @Inject
    public AlertCenter(AlertService service, UserSession session, UiTasks tasks) {
        this.service = service;
        this.session = session;
        this.tasks = tasks;
    }

    public ObservableList<AppAlert> alerts() {
        return alerts;
    }

    public ReadOnlyIntegerProperty criticalCountProperty() {
        return criticalCount;
    }

    public ReadOnlyIntegerProperty totalCountProperty() {
        return totalCount;
    }

    /** Critiques que l'utilisateur peut traiter (accès au module concerné). */
    public List<AppAlert> blocking() {
        return alerts.stream().filter(AppAlert::critical).toList();
    }

    public void refresh() {
        refresh(null);
    }

    /** Recalcule les alertes ; {@code then} reçoit la liste (sur le fil JavaFX). */
    public void refresh(Consumer<List<AppAlert>> then) {
        if (!session.isSignedIn()) {
            apply(List.of());
            return;
        }
        tasks.job(service::collect).onSuccess(all -> {
            apply(all.stream().filter(a -> session.canAccess(a.route())).toList());
            if (then != null) {
                then.accept(List.copyOf(alerts));
            }
        }).start();
    }

    private void apply(List<AppAlert> list) {
        alerts.setAll(list);
        criticalCount.set((int) list.stream().filter(AppAlert::critical).count());
        totalCount.set(list.size());
    }
}
