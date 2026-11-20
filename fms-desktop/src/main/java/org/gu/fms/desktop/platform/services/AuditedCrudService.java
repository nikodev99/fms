package org.gu.fms.desktop.platform.services;

import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.DataAccessException;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service générique « créer / modifier » : normaliser → valider (domaine) → contrôles en base
 * (unicité, références) → écrire → tracer, le tout dans UNE transaction.
 * <p>
 * Une sous-classe fournit les règles du domaine et, si besoin, ses contrôles et effets en base.
 * Même déroulé que {@code VehicleService}, sans le réécrire pour chaque référentiel.
 *
 * @param <T> entité du domaine
 */
public abstract class AuditedCrudService<T> implements CrudService<T> {

    protected final JdbcRepository<T> repository;
    protected final TransactionManager tx;
    protected final AuditLog audit;
    protected final Clock clock;
    protected final AppSettings settings;

    protected AuditedCrudService(JdbcRepository<T> repository, TransactionManager tx, AuditLog audit, Clock clock, AppSettings settings) {
        this.repository = repository;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.settings = settings;
    }

    /** Type d'entité dans le journal d'audit (ex. "driver"). */
    protected abstract String auditType();

    protected abstract T normalize(T entity);

    protected abstract List<FieldError> validate(T entity, LocalDate today);

    protected abstract Long idOf(T entity);

    protected abstract long versionOf(T entity);

    protected abstract T withIdentity(T entity, long id, long version);

    /**
     * Contrôles qui dépendent de la base, dans la transaction (unicité, références, état précédent).
     *
     * @param before état enregistré avant modification ; null pour une création
     */
    protected void check(T entity, T before, List<FieldError> errors) {
    }

    /** Ajustement final de l'entité avant écriture (ex. valeur recopiée d'une référence). */
    protected T prepare(T entity, T before) {
        return entity;
    }

    /** Effets complémentaires dans la même transaction (ex. kilométrage du véhicule). */
    protected void afterWrite(T saved, T before) {
    }

    /** Erreur à afficher si la base refuse une valeur en double (contrainte UNIQUE) ; null = erreur technique. */
    protected FieldError uniqueViolation(T entity) {
        return null;
    }

    @Override
    public List<T> findAll() {
        return repository.findAll();
    }

    @Override
    public T save(T input) {
        T entity = normalize(input);
        List<FieldError> errors = new ArrayList<>(validate(entity, today()));
        try {
            return tx.inTransaction(c -> {
                Long id = idOf(entity);
                T before = id == null ? null : repository.findById(id)
                        .orElseThrow(() -> new StaleDataException(auditType(), id, versionOf(entity)));
                check(entity, before, errors);
                ValidationException.throwIfAny(errors);
                T ready = prepare(entity, before);
                T saved;
                if (before == null) {
                    long newId = repository.insert(ready);
                    saved = withIdentity(ready, newId, 0);
                    audit.created(auditType(), newId, repository.snapshot(saved));
                } else {
                    long newVersion = repository.update(ready);
                    audit.updated(auditType(), id, repository.snapshot(before), repository.snapshot(ready), null);
                    saved = withIdentity(ready, id, newVersion);
                }
                afterWrite(saved, before);
                return saved;
            });
        } catch (DataAccessException e) {
            FieldError mapped = e.isUniqueViolation() ? uniqueViolation(entity) : null;
            if (mapped != null) {
                throw ValidationException.of(mapped);
            }
            throw e;
        }
    }

    protected final LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }
}
