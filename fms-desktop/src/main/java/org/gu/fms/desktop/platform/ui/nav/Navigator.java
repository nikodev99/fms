package org.gu.fms.desktop.platform.ui.nav;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.ui.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Affiche les écrans dans la zone de contenu. Avant de changer d'écran, demande à l'écran courant
 * s'il peut être quitté (modifications non enregistrées).
 */
@Singleton
public final class Navigator {

    private final Injector injector;
    private final RouteRegistry routes;
    private final Map<String, Screen> screens = new HashMap<>();
    private final java.util.List<Interceptor> interceptors = new java.util.ArrayList<>();
    private final UserSession session;
    private final ReadOnlyObjectWrapper<Route> current = new ReadOnlyObjectWrapper<>(this, "current");
    private StackPane host;

    /**
     * Contrôle avant d'ouvrir un écran (ex. alerte critique à traiter). Faux = l'écran n'est pas ouvert ; l'intercepteur
     * se charge d'expliquer pourquoi.
     */
    @FunctionalInterface
    public interface Interceptor {
        boolean allow(Route target);
    }

    @Inject
    public Navigator(Injector injector, RouteRegistry routes, UserSession session) {
        this.injector = injector;
        this.routes = routes;
        this.session = session;
    }

    public void addInterceptor(Interceptor interceptor) {
        interceptors.add(interceptor);
    }

    /** L'utilisateur connecté peut-il ouvrir cet écran ? */
    public boolean canOpen(Route route) {
        return session.canAccess(route.id());
    }

    /** Premier écran accessible à l'utilisateur connecté (tableau de bord s'il y a droit). */
    public java.util.Optional<Route> firstAccessible() {
        return routes.all().stream().filter(this::canOpen).findFirst();
    }

    /**
     * Ferme l'écran courant et oublie tous les écrans (déconnexion) : l'utilisateur suivant ne retrouve aucune donnée
     * affichée par le précédent.
     */
    public void reset() {
        if (host != null) {
            host.getChildren().clear();
        }
        screens.clear();
        current.set(null);
    }

    public void attach(StackPane contentHost) {
        this.host = contentHost;
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

    /** Renvoie vrai si l'écran a changé, faux si l'écran courant a refusé de se fermer. */
    public CompletableFuture<Boolean> navigate(Route route) {
        if (host == null) {
            throw new IllegalStateException("Navigator.attach() n'a pas été appelé");
        }
        if (!canOpen(route)) {
            return CompletableFuture.completedFuture(false);
        }
        for (Interceptor i : interceptors) {
            if (!i.allow(route)) {
                return CompletableFuture.completedFuture(false);
            }
        }
        if (route.equals(current.get())) {
            screen(route).onShow();
            return CompletableFuture.completedFuture(true);
        }
        Screen leaving = current.get() == null ? null : screen(current.get());
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
        Screen screen = screen(route);
        Node view = screen.view();
        host.getChildren().setAll(view);
        current.set(route);
        screen.onShow();
    }

    private Screen screen(Route route) {
        return screens.computeIfAbsent(route.id(), id -> {
            Screen s = injector.getInstance(route.screen());
            if (s instanceof Screen.RouteAware aware) {
                aware.setRoute(route);
            }
            return s;
        });
    }
}
