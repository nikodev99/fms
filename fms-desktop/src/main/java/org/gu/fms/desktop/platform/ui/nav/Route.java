package org.gu.fms.desktop.platform.ui.nav;

import org.gu.fms.desktop.platform.ui.Screen;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;

import java.util.Objects;

/**
 * Entrée de navigation.
 *
 * @param id         identifiant stable (ex. "vehicles")
 * @param titleKey   clé du libellé (menu et titre de page)
 * @param icon       icône du menu
 * @param sectionKey clé du groupe dans le menu ; {@link #FOOTER} pour le bas du menu
 * @param screen     classe de l'écran, instanciée par Guice
 */
public record Route(
        String id,
        String titleKey,
        AppIcon icon,
        String sectionKey,
        Class<? extends Screen> screen
) {
    public static final String FOOTER = "footer";

    public Route {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(sectionKey, "sectionKey");
        Objects.requireNonNull(screen, "screen");
    }
}
