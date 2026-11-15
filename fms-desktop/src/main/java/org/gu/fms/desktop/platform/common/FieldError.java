package org.gu.fms.desktop.platform.common;

import java.util.List;
import java.util.Objects;

/**
 * Erreur de validation rattachée à un champ.
 *
 * Le domaine ne produit jamais de texte affiché : il renvoie une clé de message
 * (ex. "vehicle.registration.taken") et ses arguments. Chaque interface (Desktop, Web)
 * traduit la clé dans sa langue.
 *
 * @param field      nom du champ métier (ex. "registration") ; {@code null} pour une erreur globale
 * @param messageKey clé de message
 * @param arguments       arguments du message, dans l'ordre
 */
public final record FieldError(
        String field,
        String messageKey,
        List<Object> arguments
) {
    public FieldError {
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(arguments);
    }

    public static FieldError of (String field, String messageKey, Object... args) {
        return new FieldError(field, messageKey, List.of(args));
    }

    public boolean isGlobal() {
        return field == null;
    }
}
