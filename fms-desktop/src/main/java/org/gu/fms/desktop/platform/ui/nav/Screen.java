package org.gu.fms.desktop.platform.ui.nav;

import javafx.scene.Node;

import java.util.concurrent.CompletableFuture;

public interface Screen {
    /**
     * Écran affiché dans la zone de contenu de la coque.
     * Une instance par route, créée par Guice à la première visite puis conservée.
     */
    Node view();

    /** Chaque fois que l'écran devient visible : rafraîchir les données ici. */
    default void onShow() {
    }

    /**
     * Avant de quitter l'écran. Renvoie faux pour rester (ex. modifications non enregistrées
     * et l'utilisateur annule).
     */
    default CompletableFuture<Boolean> confirmLeave() {
        return CompletableFuture.completedFuture(true);
    }

    /** Écran partagé entre plusieurs routes (ex. « module en construction ») : reçoit sa route. */
    interface RouteAware {
        void setRoute(Route route);
    }

    /**
     * Écran affiché dans un onglet ou une section d'un autre écran : il ne dessine ni marge de page
     * ni titre principal (le conteneur s'en charge). Appelé avant {@link #view()}.
     */
    interface Embeddable {
        void embed();
    }
}
