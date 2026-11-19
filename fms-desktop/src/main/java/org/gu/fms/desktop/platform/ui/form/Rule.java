package org.gu.fms.desktop.platform.ui.form;

import java.util.List;

/**
 * Règle de validation d'un champ, appelée seulement si le champ a une valeur
 * (l'obligation de saisie est gérée à part par {@code required()}).
 * <p>
 * Renvoie {@code null} si la valeur est acceptable, sinon un {@link Issue} (clé de message + arguments).
 */
public interface Rule<T> {

    Issue check(T value);

    record Issue(String messageKey, List<Object> args) {
        public static Issue of(String messageKey, Object... args) {
            return new Issue(messageKey, List.of(args));
        }
    }

}
