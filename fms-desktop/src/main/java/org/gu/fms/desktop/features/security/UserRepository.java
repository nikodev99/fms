package org.gu.fms.desktop.features.security;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.desktop.platform.security.PasswordHasher;
import org.gu.fms.domain.security.AppUser;
import org.gu.fms.domain.security.UserRole;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Comptes utilisateurs. Les empreintes de mots de passe ne sortent du dépôt que pour la vérification. */
@Singleton
public final class UserRepository extends JdbcRepository<AppUser> {

    private static final String SELECT = "SELECT u.*, (SELECT group_concat(p.module) FROM user_permission p"
            + " WHERE p.user_id = u.id) AS modules FROM app_user u";

    @Inject
    public UserRepository(Jdbc jdbc, Clock clock) {
        super(jdbc, clock);
    }

    @Override
    protected String table() {
        return "app_user";
    }

    @Override
    protected RowMapper<AppUser> mapper() {
        return row -> {
            String m = row.string("modules");
            Set<String> modules = m == null || m.isBlank() ? Set.of()
                    : Arrays.stream(m.split(",")).map(String::strip).collect(Collectors.toSet());
            return new AppUser(row.longValue("id"), row.longValue("version"), row.string("username"),
                    row.string("display_name"), row.enumValue("role", UserRole.class), row.bool("active"),
                    row.bool("must_change_password"), row.intValue("failed_attempts"), row.instant("locked_until"),
                    row.instant("last_login_at"), modules);
        };
    }

    @Override
    public Optional<AppUser> findById(long id) {
        return jdbc.queryOne(SELECT + " WHERE u.id = ?", mapper(), id);
    }

    @Override
    public List<AppUser> findAll() {
        return jdbc.query(SELECT + " ORDER BY u.role, u.username", mapper());
    }

    public Optional<AppUser> findByUsername(String username) {
        return jdbc.queryOne(SELECT + " WHERE u.username = ? COLLATE NOCASE", mapper(), username);
    }

    @Override
    protected Long idOf(AppUser u) {
        return u.id();
    }

    @Override
    protected long versionOf(AppUser u) {
        return u.version();
    }

    /** Colonnes du profil (sans mot de passe ni compteurs de connexion). */
    @Override
    protected Map<String, Object> columnsOf(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", u.username());
        m.put("display_name", u.displayName());
        m.put("role", u.role());
        m.put("active", u.active());
        m.put("must_change_password", u.mustChangePassword());
        return m;
    }

    @Override
    public Map<String, Object> snapshot(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>(columnsOf(u));
        m.put("modules", u.modules().stream().sorted().collect(Collectors.joining(",")));
        return m;
    }

    @Override
    public long insert(AppUser u) {
        throw new UnsupportedOperationException("Utiliser insert(user, hash)");
    }

    public long insert(AppUser u, PasswordHasher.Hash hash) {
        Instant now = clock.instant();
        return jdbc.insert("INSERT INTO app_user (version, username, display_name, role, password_hash, password_salt,"
                        + " password_iterations, must_change_password, active, failed_attempts, created_at, updated_at)"
                        + " VALUES (0, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                u.username(), u.displayName(), u.role(), hash.hash(), hash.salt(), hash.iterations(),
                u.mustChangePassword(), u.active(), now, now);
    }

    public Optional<PasswordHasher.Hash> credentials(long userId) {
        return jdbc.queryOne("SELECT password_hash, password_salt, password_iterations FROM app_user WHERE id = ?",
                row -> new PasswordHasher.Hash(row.string("password_hash"), row.string("password_salt"),
                        row.intValue("password_iterations")), userId);
    }

    public void setPassword(long userId, PasswordHasher.Hash hash, boolean mustChange) {
        jdbc.update("UPDATE app_user SET password_hash = ?, password_salt = ?, password_iterations = ?,"
                        + " must_change_password = ?, failed_attempts = 0, locked_until = NULL, version = version + 1,"
                        + " updated_at = ? WHERE id = ?",
                hash.hash(), hash.salt(), hash.iterations(), mustChange, clock.instant(), userId);
    }

    public void recordSuccess(long userId) {
        jdbc.update("UPDATE app_user SET failed_attempts = 0, locked_until = NULL, last_login_at = ? WHERE id = ?",
                clock.instant(), userId);
    }

    public void recordFailure(long userId, int failures, Instant lockedUntil) {
        jdbc.update("UPDATE app_user SET failed_attempts = ?, locked_until = ? WHERE id = ?", failures, lockedUntil,
                userId);
    }

    public void replaceModules(long userId, Set<String> modules) {
        jdbc.update("DELETE FROM user_permission WHERE user_id = ?", userId);
        for (String m : modules.stream().sorted().toList()) {
            jdbc.update("INSERT INTO user_permission (user_id, module) VALUES (?, ?)", userId, m);
        }
    }

    public long countActiveOwners() {
        return jdbc.queryLong("SELECT COUNT(*) FROM app_user WHERE role = 'OWNER' AND active = 1");
    }
}
