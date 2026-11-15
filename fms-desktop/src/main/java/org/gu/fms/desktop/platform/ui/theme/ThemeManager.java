package org.gu.fms.desktop.platform.ui.theme;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Parent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

@Singleton
public final class ThemeManager {
    private static final String PREF_KEY = "theme.mode";
    private static final String DARK_CLASS = "theme-dark";
    private static final long SYSTEM_POLL_SECONDS = 5L;

    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
    private final ReadOnlyObjectWrapper<ThemeMode> mode = new ReadOnlyObjectWrapper<>(this, "mode");
    private final ReadOnlyBooleanWrapper dark = new ReadOnlyBooleanWrapper(this, "dark");
    private final ScheduledExecutorService watcher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fms-theme-watcher");
        t.setDaemon(true);
        return t;
    });

    private Parent root;
    private ScheduledFuture<?> systemWatch;
    private Boolean appliedDark;

    @Inject
    public ThemeManager() {
        mode.set(readSavedMode());
    }

    /** À appeler avant {@code stage.Show()} pour éviter un flash du mauvais thème. */
    public void attach(Parent root) {
        this.root = root;
        refresh();
    }

    public void setMode(ThemeMode newMode) {
        if (newMode == null || newMode == this.mode.get()) {
            return;
        }

        mode.set(newMode);
        prefs.put(PREF_KEY, newMode.name());
        refresh();
    }

    public ThemeMode getMode() {
        return mode.get();
    }

    public ReadOnlyObjectProperty<ThemeMode> modeProperty() {
        return mode.getReadOnlyProperty();
    }

    public boolean isDark() {
        return dark.get();
    }

    public ReadOnlyBooleanProperty darkProperty() {
        return dark.getReadOnlyProperty();
    }

    public void shutdown() {
        watcher.shutdownNow();
    }

    private void refresh() {
        switch (mode.get()) {
            case LIGHT -> {stopWatching();apply(false);}
            case DARK -> {stopWatching(); apply(true);}
            case SYSTEM -> {apply(SystemTheme.isDark()); startWatching();}
        }
    }

    private void startWatching() {
        if (systemWatch != null) {
            return;
        }
        systemWatch = watcher.scheduleWithFixedDelay(() -> {
            boolean systemDark = SystemTheme.isDark();
            Platform.runLater(() -> {
                if (mode.get() == ThemeMode.SYSTEM) {
                    apply(systemDark);
                }
            });
        }, SYSTEM_POLL_SECONDS, SYSTEM_POLL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopWatching() {
        if (systemWatch != null) {
            systemWatch.cancel(true);
            systemWatch = null;
        }
    }

    private void apply(boolean useDark) {
        if (appliedDark != null && appliedDark == useDark) {
            return;
        }

        appliedDark = useDark;
        Application.setUserAgentStylesheet(useDark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet()
        );

        if (root != null) {
            root.getStyleClass().remove(DARK_CLASS);
            if (useDark) {
                root.getStyleClass().add(DARK_CLASS);
            }
        }
        dark.set(useDark);
    }

    private ThemeMode readSavedMode() {
        try {
            return ThemeMode.valueOf(prefs.get(PREF_KEY, ThemeMode.SYSTEM.name()));
        }catch (IllegalArgumentException e) {
            return ThemeMode.SYSTEM;
        }
    }
}
