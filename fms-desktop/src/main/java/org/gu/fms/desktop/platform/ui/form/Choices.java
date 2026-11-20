package org.gu.fms.desktop.platform.ui.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** Construction des listes de choix à partir des référentiels. */
public final class Choices {

    private Choices() {
    }

    /**
     * Éléments actifs, plus l'élément déjà enregistré même s'il a été désactivé depuis
     * (une fiche existante ne doit pas perdre sa valeur à l'ouverture).
     */
    public static <T> List<T> activePlus(List<T> all, Predicate<T> active, Function<T, Long> id, Long currentId) {
        List<T> result = new ArrayList<>();
        for (T item : all) {
            if (active.test(item) || Objects.equals(id.apply(item), currentId)) {
                result.add(item);
            }
        }
        return result;
    }

    public static <T> T byId(List<T> all, Function<T, Long> id, Long wanted) {
        if (wanted == null) {
            return null;
        }
        return all.stream().filter(i -> wanted.equals(id.apply(i))).findFirst().orElse(null);
    }
}
