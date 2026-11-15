package org.gu.fms.desktop.platform.ui;

import java.util.Locale;

/**
 * Tonalité sémantique, distincte de la couleur de marque : un composant ne choisit jamais
 * une couleur, il déclare une tonalité, et la feuille de style décide (thème clair ou sombre).
 */
public enum Tone {
    NEUTRAL,
    INFO,
    SUCCESS,
    WARNING,
    DANGER;

    /** Classe CSS correspondante : neutral, info, success, warning, danger. */
    public String styleClass() {
        return name().toLowerCase(Locale.ROOT);
    }
}
