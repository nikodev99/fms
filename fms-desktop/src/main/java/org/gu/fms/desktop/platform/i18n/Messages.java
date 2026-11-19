package org.gu.fms.desktop.platform.i18n;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Textes de l'interface (fichier i18n/messages*.properties, UTF-8).
 * <p>
 * Conventions de clés
 * – nav.*, action.*, common.* : textes partagés
 * – enum.&lt;Type&gt;.&lt;VALEUR&gt; : libellés d'énumérations ({@link #label(Enum)})
 * – validation.* et &lt;module&gt;.* : messages d'erreur produits par le domaine ({@link #error(FieldError)}).
 * Apostrophe typographique ’ dans les textes : l'apostrophe droite ' a un sens spécial pour MessageFormat.
 * Une clé absente s'affiche « ! Clé ! » pour être repérée immédiatement.
 */
@Singleton
public final class Messages {
    private final ResourceBundle bundle;
    private final AppSettings settings;

    @Inject
    public Messages(AppSettings settings) {
        this.settings = settings;
        this.bundle = ResourceBundle.getBundle("i18n.messages", settings.locale());
    }

    public String get(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        }catch (MissingResourceException e) {
            return "!" + key + "!";
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, settings.locale()).format(args);
    }

    public String label(Enum<?> value) {
        return value == null ? "": get("enum." + value.getDeclaringClass().getSimpleName() + "." + value.name());
    }

    public String error(FieldError error) {
        return get(error.messageKey(), error.args().toArray());
    }

    public ResourceBundle bundle() {
        return bundle;
    }
}
