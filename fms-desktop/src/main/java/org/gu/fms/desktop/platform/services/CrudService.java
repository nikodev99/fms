package org.gu.fms.desktop.platform.services;

import org.gu.fms.domain.common.ValidationException;

import java.util.List;

/**
 * Contrat minimal d'un service de gestion d'entités, utilisé par l'écran générique de liste/édition.
 * Les opérations sont appelées hors du fil JavaFX (tâches de fond).
 *
 * @param <T> entité du domaine
 */
public interface CrudService<T> {
    List<T> findAll();

    /**
     * Crée ou met à jour, puis renvoie l'entité telle qu'enregistrée (identifiant et version à jour).
     *
     * @throws ValidationException règle métier non respectée ; rien n'a été écrit
     * @throws org.gu.fms.desktop.platform.db.StaleDataException modification concurrente
     */
    T save(T entity);
}
