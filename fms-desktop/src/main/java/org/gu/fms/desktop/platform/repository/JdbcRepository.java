package org.gu.fms.desktop.platform.repository;

import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.db.StaleDataException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Dépôt générique pour une table qui suit les conventions FMS :
 * <ul>
 *   <li>{@code id INTEGER PRIMARY KEY AUTOINCREMENT}</li>
 *   <li>{@code version INTEGER NOT NULL} : verrouillage optimiste, incrémenté à chaque modification</li>
 *   <li>{@code created_at}, {@code updated_at} : horodatage UTC ISO-8601, gérés ici</li>
 * </ul>
 * Une sous-classe décrit seulement sa table, sa lecture et ses colonnes métier. Les données financières ne sont
 * jamais effacées (instructions §15) ; seuls les référentiels inutilisés peuvent l'être ({@link #delete}).
 *
 * @param <T> entité du domaine
 */
public abstract class JdbcRepository<T> {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    protected final Jdbc jdbc;
    protected final Clock clock;

    protected JdbcRepository(Jdbc jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private String tableName() {
        String table = table();
        requireIdentifier(table);
        return table;
    }

    /** Nom de la table. */
    protected abstract String table();

    /** Lecture d'une ligne complète (y compris id et version). */
    protected abstract RowMapper<T> mapper();

    protected abstract Long idOf(T entity);

    protected abstract long versionOf(T entity);

    /** Colonnes métier, hors id, version et horodatages, dans un ordre stable (LinkedHashMap). */
    protected abstract Map<String, Object> columnsOf(T entity);

    /** Tri par défaut de {@link #findAll()}. Texte SQL de confiance (jamais issu d'une saisie). */
    protected String defaultOrder() {
        return "id";
    }

    /** État d'une entité sous forme colonne → valeur, pour le journal d'audit. */
    public Map<String, Object> snapshot(T entity) {
        return columnsOf(entity);
    }

    public Optional<T> findById(long id) {
        return jdbc.queryOne("SELECT * FROM " + tableName() + " WHERE id = ?", mapper(), id);
    }

    public List<T> findAll() {
        return jdbc.query("SELECT * FROM " + tableName() + " ORDER BY " + defaultOrder(), mapper());
    }

    public long count() {
        return jdbc.queryLong("SELECT COUNT(*) FROM " + tableName());
    }

    /** Une autre ligne a-t-elle déjà cette valeur ? (contrôle d'unicité avant écriture). */
    public boolean existsWithValue(String column, Object value, Long excludingId) {
        requireIdentifier(column);
        if (excludingId == null) {
            return jdbc.exists("SELECT 1 FROM " + tableName() + " WHERE " + column + " = ? LIMIT 1", value);
        }
        return jdbc.exists("SELECT 1 FROM " + tableName() + " WHERE " + column + " = ? AND id <> ? LIMIT 1",
                value, excludingId);
    }

    /** Insère et renvoie l'identifiant attribué. La version de départ est 0. */
    public long insert(T entity) {
        if (idOf(entity) != null) {
            throw new IllegalArgumentException("Insertion d'une entité qui a déjà un identifiant : " + idOf(entity));
        }
        Map<String, Object> columns = columnsOf(entity);
        Instant now = clock.instant();
        List<String> names = new ArrayList<>(columns.keySet());
        names.forEach(JdbcRepository::requireIdentifier);
        List<Object> values = new ArrayList<>(columns.values());
        names.add("version");
        values.add(0L);
        names.add("created_at");
        values.add(now);
        names.add("updated_at");
        values.add(now);

        String sql = "INSERT INTO " + tableName() + " (" + String.join(", ", names) + ") VALUES ("
                + String.join(", ", java.util.Collections.nCopies(names.size(), "?")) + ")";
        return jdbc.insert(sql, values.toArray());
    }

    /**
     * Met à jour si la version lue est toujours la version en base ; renvoie la nouvelle version.
     *
     * @throws StaleDataException si quelqu'un a modifié l'enregistrement entre-temps
     */
    public long update(T entity) {
        Long id = idOf(entity);
        if (id == null) {
            throw new IllegalArgumentException("Mise à jour d'une entité sans identifiant");
        }
        long expected = versionOf(entity);
        Map<String, Object> columns = columnsOf(entity);
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName()).append(" SET ");
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> e : columns.entrySet()) {
            requireIdentifier(e.getKey());
            sql.append(e.getKey()).append(" = ?, ");
            values.add(e.getValue());
        }
        sql.append("version = version + 1, updated_at = ? WHERE id = ? AND version = ?");
        values.add(clock.instant());
        values.add(id);
        values.add(expected);

        if (jdbc.update(sql.toString(), values.toArray()) != 1) {
            throw new StaleDataException(tableName(), id, expected);
        }
        return expected + 1;
    }

    /**
     * Suppression physique, réservée aux référentiels (catégories, types…) : une ligne encore référencée est refusée
     * par la base (clé étrangère), une ligne protégée par un déclencheur aussi.
     *
     * @throws StaleDataException si la ligne a changé ou disparu entre-temps
     */
    public void delete(long id, long expectedVersion) {
        if (jdbc.update("DELETE FROM " + tableName() + " WHERE id = ? AND version = ?", id, expectedVersion) != 1) {
            throw new StaleDataException(tableName(), id, expectedVersion);
        }
    }

    private static void requireIdentifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Nom SQL invalide : " + name);
        }
    }
}
