package org.gu.fms.desktop.platform.services;

/**
 * Référentiel dont les éléments inutilisés peuvent être supprimés (catégories, types de documents, types
 * d'intervention…). Un élément système n'est jamais supprimable ; un élément utilisé est refusé par la base et
 * l'utilisateur est invité à le désactiver.
 *
 * @param <T> entité du domaine
 */
public interface DeletableService<T> {

    /** Faux pour un élément système (le bouton « Supprimer » n'est pas proposé). Sans lecture en base. */
    boolean deletable(T entity);

    /**
     * Supprime l'élément et trace l'opération.
     *
     * @throws org.gu.fms.domain.common.ValidationException élément système ou encore utilisé
     */
    void delete(T entity);
}
