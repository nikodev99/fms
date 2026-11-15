package org.gu.fms.desktop.platform.ui.nav;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public final class Navigator {
    private final Injector injector;
    private final RouteRegistry routes;
    private final Map<String, Screen> screens = new HashMap<>();
    private final ReadOnlyObjectWrapper<Route> current = new ReadOnlyObjectWrapper<>(this, "current");
    private StackPane host;

    @Inject
    public Navigator(Injector injector, RouteRegistry routes) {
        this.injector = injector;
        this.routes = routes;
    }

    public void attach(StackPane host) {
        this.host = host;
    }

    public ReadOnlyObjectProperty<Route> currentProperty() {
        return current.getReadOnlyProperty();
    }

    public Route getCurrent() {
        return current.get();
    }

    public CompletableFuture<Boolean> navigate(String routeId) {
        return navigate(routes.get(routeId));
    }

    public CompletableFuture<Boolean> navigate(Route route) {
        if (host == null) {
            throw new IllegalStateException("Navigator.attach() n'a pas été appelé");
        }
        if (route.equals(current.get())) {
            getScreen(route).onShow();
            return CompletableFuture.completedFuture(true);
        }
        Screen leaving = current.get() == null ? null : getScreen(current.get());
        CompletableFuture<Boolean> canLeave = leaving == null
                ? CompletableFuture.completedFuture(true)
                : leaving.confirmLeave();

        return canLeave.thenApply(ok -> {
            if (ok) {
                show(route);
            }
            return ok;
        });
    }

    private void show(Route route) {
        Screen screen = getScreen(route);
        Node view = screen.view();
        host.getChildren().setAll(view);
        current.set(route);
        screen.onShow();
    }

    private Screen getScreen(Route route) {
        return screens.computeIfAbsent(route.id(), id -> {
            Screen screen = injector.getInstance(route.screen());
            if (screen instanceof Screen.RouteAware aware) {
                aware.setRoute(route);
            }
            return screen;
        });
    }
}
