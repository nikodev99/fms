package org.gu.fms.desktop.platform.ui.overlay;

import jakarta.inject.Singleton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Couches superposées à la fenêtre principale, dans cet ordre :
 * contenu → tiroirs → dialogues → notifications (toujours au-dessus).
 */
@Singleton
public final class OverlayHost {

    private StackPane root;
    private final VBox toastLayer = new VBox(8);

    public OverlayHost() {
        toastLayer.getStyleClass().add("toast-layer");
        toastLayer.setAlignment(Pos.BOTTOM_RIGHT);
        toastLayer.setPadding(new Insets(0, 24, 40, 0));
        toastLayer.setPickOnBounds(false);
        toastLayer.setMouseTransparent(false);
        toastLayer.setFillWidth(false);
    }

    /** À appeler une fois : {@code root} est la racine de la scène, dont le premier enfant est la coque. */
    public void attach(StackPane root) {
        this.root = root;
        root.getChildren().add(toastLayer);
    }

    /** Ajoute une couche sous les notifications (tiroir, dialogue, écran de connexion…). */
    public void push(Node layer) {
        requireAttached();
        root.getChildren().add(root.getChildren().indexOf(toastLayer), layer);
    }

    public void remove(Node layer) {
        if (root != null) {
            root.getChildren().remove(layer);
        }
    }

    VBox toastLayer() {
        return toastLayer;
    }

    /** Une couche modale (tiroir ou dialogue) est-elle ouverte ? */
    public boolean hasModal() {
        return root != null && root.getChildren().stream().anyMatch(n -> n.getStyleClass().contains("overlay-layer"));
    }

    private void requireAttached() {
        if (root == null) {
            throw new IllegalStateException("OverlayHost.attach() n'a pas été appelé");
        }
    }
}
