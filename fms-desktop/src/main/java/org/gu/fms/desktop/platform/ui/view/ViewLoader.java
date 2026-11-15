package org.gu.fms.desktop.platform.ui.view;

import com.google.inject.Injector;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.gu.fms.desktop.platform.i18n.Messages;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

/**
 * Chargement des vues FXML.
 *  - Les contrôleurs déclarés par {@code fx:controller} être créés par Guice (injection par constructeur).
 *  - Les textes {@code %clé} viennent de {@link Messages}.
 */
@Singleton
public final class ViewLoader {
    private final Injector injector;
    private final Messages messages;

    @Inject
    public ViewLoader(Injector injector, Messages messages) {
        this.injector = injector;
        this.messages = messages;
    }

    /** Vue dont le contrôleur est déclaré dans le FXML ({@code fx:controller}) et créé par Guice. */
    public <C> View<C> load(String resource) {
        FXMLLoader loader = loader(resource);
        loader.setControllerFactory(injector::getInstance);
        try {
            Parent root = loader.load();
            return new View<>(root, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("Chargement impossible : " + resource, e);
        }
    }

    /** Vue dont le contrôleur existe déjà (le FXML ne déclare pas de fx : controller). */
    public Parent load(String resource, Object controller) {
        FXMLLoader loader = loader(resource);
        loader.setController(controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Chargement impossible : " + resource, e);
        }
    }

    private FXMLLoader loader(String resource) {
        URL url = ViewLoader.class.getResource(resource);
        if (url == null) {
            throw new IllegalArgumentException("Vue introuvable : " + resource);
        }
        FXMLLoader loader = new FXMLLoader(url, messages.bundle());
        loader.setClassLoader(ViewLoader.class.getClassLoader());
        return loader;
    }
}
