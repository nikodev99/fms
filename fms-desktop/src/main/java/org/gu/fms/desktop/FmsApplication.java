package org.gu.fms.desktop;

import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.gu.fms.desktop.app.AppModule;
import org.gu.fms.desktop.app.AppPaths;
import org.gu.fms.desktop.app.AppRoutes;
import org.gu.fms.desktop.app.shell.ShellController;
import org.gu.fms.desktop.platform.db.Database;
import org.gu.fms.desktop.platform.ui.nav.Navigator;
import org.gu.fms.desktop.platform.ui.overlay.OverlayHost;
import org.gu.fms.desktop.platform.ui.task.UiTasks;
import org.gu.fms.desktop.platform.ui.theme.ThemeManager;
import org.gu.fms.desktop.platform.ui.view.View;
import org.gu.fms.desktop.platform.ui.view.ViewLoader;

import java.io.InputStream;
import java.util.Objects;

public class FmsApplication extends Application {
    public static final String STYLESHEET = "/org/gu/fms/desktop/platform/ui/theme/fms.css";
    public static final String SHELL = "/org/gu/fms/desktop/app/shell/shell.fxml";
    public static final String ICON_PATTERN = "/org/gu/fms/desktop/images/fms-logo-%d.png";
    public static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final double MIN_WIDTH = 1024;
    private static final double MIN_HEIGHT = 640;

    private Injector injector;
    private Throwable startupError;

    @Override
    public void init() {
        try {
            injector = Guice.createInjector(new AppModule(AppPaths.resolve()));
            //injector.getInstance(Database.class).open();
            //injector.getInstance(MigrationEngine.class).migrate();
        }catch (RuntimeException e) {
            startupError = e;
        }
    }

    @Override
    public void start(Stage stage) {
        if (startupError != null) {
            showStartupError(startupError);
            return;
        }
        
        ViewLoader views = injector.getInstance(ViewLoader.class);
        View<ShellController> shell = views.load(SHELL);
        StackPane root = (StackPane) shell.root();

        Scene scene = new Scene(root, 1280, 760);
        scene.getStylesheets().add(Objects.requireNonNull(FmsApplication.class.getResource(STYLESHEET)).toExternalForm());

        injector.getInstance(ThemeManager.class).attach(root);
        injector.getInstance(OverlayHost.class).attach(root);
        injector.getInstance(Navigator.class).navigate(AppRoutes.DASHBOARD);

        for (int size: ICON_SIZES) {
            try (InputStream icon = FmsApplication.class.getResourceAsStream(String.format(ICON_PATTERN, size))) {
                if (icon != null) {
                    stage.getIcons().add(new Image(icon));
                }
            } catch (java.io.IOException e) {
                // icône facultative : la fenêtre s'ouvre avec l'icône Java par défaut
            }
        }
        stage.setTitle("FMS — Gestion de flotte");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        if (injector == null) {
            return;
        }
        injector.getInstance(UiTasks.class).shutdown();
        injector.getInstance(ThemeManager.class).shutdown();
        injector.getInstance(Database.class).close();
    }

    /** Accès au conteneur (tests d'interface). */
    public Injector injector() {
        return injector;
    }

    private void showStartupError(Throwable error) {
        System.getLogger(FmsApplication.class.getName()).log(System.Logger.Level.ERROR, "Démarrage impossible", error);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("FMS");
        alert.setHeaderText("L’application ne peut pas démarrer.");
        alert.setContentText("La base de données n’a pas pu être préparée. Aucune donnée n’a été modifiée."
                + " Transmettez le détail ci-dessous au support.");
        TextArea details = new TextArea(String.valueOf(error.getMessage()));
        details.setEditable(false);
        details.setWrapText(true);
        alert.getDialogPane().setExpandableContent(details);
        alert.getDialogPane().setExpanded(true);
        alert.showAndWait();
        Platform.exit();
    }
}
