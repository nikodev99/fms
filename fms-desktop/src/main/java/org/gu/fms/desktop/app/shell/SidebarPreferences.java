package org.gu.fms.desktop.app.shell;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.prefs.Preferences;

/**
 * Préférences du menu latéral, propres à l'utilisateur Windows (comme le thème), pas à la base de données.
 * <p>
 *  - {@code autoCollapse} : le menu se réduit aux icônes quand la fenêtre devient étroite, et se déplie
 *    quand elle redevient large ;
 *  - {@code collapsed} : dernier état choisi à la main (bouton du menu), retrouvé au démarrage si la fenêtre est large.
 */
@Singleton
public final class SidebarPreferences {

    private static final String AUTO = "sidebar.autoCollapse";
    private static final String COLLAPSED = "sidebar.collapsed";

    private final Preferences prefs = Preferences.userNodeForPackage(SidebarPreferences.class);
    private final BooleanProperty autoCollapse = new SimpleBooleanProperty(this, "autoCollapse");

    @Inject
    public SidebarPreferences() {
        autoCollapse.set(prefs.getBoolean(AUTO, true));
        autoCollapse.addListener((obs, o, value) -> prefs.putBoolean(AUTO, value));
    }

    public BooleanProperty autoCollapseProperty() {
        return autoCollapse;
    }

    public boolean savedCollapsed() {
        return prefs.getBoolean(COLLAPSED, false);
    }

    public void saveCollapsed(boolean collapsed) {
        prefs.putBoolean(COLLAPSED, collapsed);
    }
}
