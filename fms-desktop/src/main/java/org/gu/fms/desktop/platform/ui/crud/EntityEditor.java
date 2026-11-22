package org.gu.fms.desktop.platform.ui.crud;

import org.gu.fms.desktop.platform.ui.form.Form;

/**
 * Formulaire d'une entité, pour l'écran générique {@link CrudScreen}.
 * Une instance par ouverture du tiroir : elle part de l'entité existante (modification) ou de rien (création).
 */
public interface EntityEditor<T> {

    /** Titre du tiroir (« Nouveau véhicule », « Modifier BZ 1234 A »). */
    String title();

    /** Sous-titre facultatif. */
    default String subtitle() {
        return null;
    }

    Form form();

    /** Contenu affiché au-dessus du formulaire (ex. chiffres, avertissement) ; facultatif. */
    default javafx.scene.Node header() {
        return null;
    }

    /** Libellé du bouton d'enregistrement (ex. « Clôturer ») ; null = « Enregistrer ». */
    default String saveLabel() {
        return null;
    }

    /** Consultation seule (enregistrement figé) : le tiroir n'a pas de bouton « Enregistrer ». */
    default boolean readOnly() {
        return false;
    }

    /** Construit l'entité à enregistrer. Appelé seulement si {@code form().validate()} a réussi. */
    T toEntity();
}
