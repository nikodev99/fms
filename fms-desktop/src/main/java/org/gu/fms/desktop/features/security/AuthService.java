package org.gu.fms.desktop.features.security;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.security.PasswordHasher;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.security.AppUser;
import org.gu.fms.domain.security.UserRole;
import org.gu.fms.domain.security.UserRules;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Connexion, premier propriétaire, changement de mot de passe, clé de récupération.
 *
 * <ul>
 *   <li>tant qu'aucun utilisateur n'existe, les identifiants par défaut ({@value #DEFAULT_USERNAME} /
 *       {@value #DEFAULT_PASSWORD}) ouvrent une session « initiale » qui ne permet QUE de créer le propriétaire ; dès
 *       qu'un utilisateur existe, ils sont refusés ;</li>
 *   <li>5 échecs consécutifs verrouillent le compte 1 minute, puis 2, 4… jusqu'à 30 minutes ;</li>
 *   <li>hors ligne, pas de réinitialisation par e-mail : la clé de récupération (montrée une seule fois à la création
 *       du propriétaire) permet de redéfinir le mot de passe d'un propriétaire ; elle est alors remplacée.</li>
 * </ul>
 * La connexion protège l'application ; le fichier de base de données lui-même n'est pas chiffré.
 */
@Singleton
public final class AuthService {

    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin";
    static final String AUDIT_TYPE = "app_user";
    static final String RECOVERY_HASH = "security.recoveryHash";
    static final String RECOVERY_SALT = "security.recoverySalt";
    static final String RECOVERY_ITERATIONS = "security.recoveryIterations";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final UserSession session;
    private final SettingsStore settings;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final Clock clock;

    @Inject
    public AuthService(UserRepository users, PasswordHasher hasher, UserSession session, SettingsStore settings,
                       TransactionManager tx, AuditLog audit, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.session = session;
        this.settings = settings;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
    }

    public boolean hasUsers() {
        return users.count() > 0;
    }

    /** Résultat d'une connexion réussie. */
    public record Login(UserSession.User user, boolean mustChangePassword) {
    }

    /**
     * Vérifie les identifiants et ouvre la session.
     *
     * @throws ValidationException identifiants refusés (message volontairement identique pour un nom inconnu et un
     *                             mauvais mot de passe), compte désactivé ou verrouillé
     */
    public Login login(String username, char[] password) {
        String name = username == null ? "" : username.strip();
        if (!hasUsers()) {
            if (DEFAULT_USERNAME.equalsIgnoreCase(name) && DEFAULT_PASSWORD.equals(new String(password))) {
                UserSession.User bootstrap = new UserSession.User(null, DEFAULT_USERNAME, "Utilisateur local",
                        UserSession.Role.OWNER, Set.of(), true);
                session.signIn(bootstrap);
                return new Login(bootstrap, true);
            }
            throw refused();
        }
        // Les échecs sont enregistrés (compteur, verrouillage, audit) AVANT le refus : la transaction est validée.
        Object outcome = tx.inTransaction(c -> {
            Optional<AppUser> found = users.findByUsername(name);
            if (found.isEmpty()) {
                return refused();
            }
            AppUser u = found.get();
            Instant now = clock.instant();
            if (u.lockedUntil() != null && u.lockedUntil().isAfter(now)) {
                long seconds = Math.max(1, java.time.Duration.between(now, u.lockedUntil()).toSeconds());
                return ValidationException.of(FieldError.of(null, "auth.locked", seconds));
            }
            PasswordHasher.Hash stored = users.credentials(u.id()).orElseThrow();
            if (!hasher.verify(password, stored)) {
                int failures = u.failedAttempts() + 1;
                java.time.Duration lock = UserRules.lockFor(failures);
                users.recordFailure(u.id(), failures, lock.isZero() ? null : now.plus(lock));
                audit.event(AUDIT_TYPE, u.id(), AuditAction.SECURITY, Map.of("event", "login_failed",
                        "failures", failures), null);
                return refused();
            }
            if (!u.active()) {
                return ValidationException.of(FieldError.of(null, "auth.inactive"));
            }
            users.recordSuccess(u.id());
            audit.event(AUDIT_TYPE, u.id(), AuditAction.SECURITY, Map.of("event", "login"), null);
            return u;
        });
        if (outcome instanceof ValidationException refusal) {
            throw refusal;
        }
        AppUser u = (AppUser) outcome;
        UserSession.User signed = toSession(u);
        session.signIn(signed);
        return new Login(signed, u.mustChangePassword());
    }

    private static ValidationException refused() {
        return ValidationException.of(FieldError.of(null, "auth.refused"));
    }

    static UserSession.User toSession(AppUser u) {
        return new UserSession.User(u.id(), u.username(), u.displayName(),
                u.role() == UserRole.OWNER ? UserSession.Role.OWNER : UserSession.Role.USER, u.modules(), false);
    }

    public void logout() {
        UserSession.User u = session.currentUser();
        if (u.id() != null) {
            audit.event(AUDIT_TYPE, u.id(), AuditAction.SECURITY, Map.of("event", "logout"), null);
        }
        session.signOut();
    }

    /**
     * Crée le premier propriétaire (session initiale seulement) et le connecte. Renvoie la clé de récupération, à
     * noter : elle n'est montrée qu'une fois.
     */
    public String createFirstOwner(String username, String displayName, char[] password, char[] confirmation) {
        if (!session.currentUser().bootstrap()) {
            throw ValidationException.of(FieldError.of(null, "security.ownerOnly"));
        }
        AppUser draft = UserRules.normalize(new AppUser(null, 0, username, displayName, UserRole.OWNER, true, false, 0,
                null, null, Set.of()));
        List<FieldError> errors = new ArrayList<>(UserRules.validate(draft));
        errors.addAll(passwordErrors("password", password, confirmation));
        if (draft.username() != null && DEFAULT_USERNAME.equals(draft.username())
                && DEFAULT_PASSWORD.equals(new String(password))) {
            errors.add(FieldError.of("password", "auth.defaultForbidden"));
        }
        ValidationException.throwIfAny(errors);
        String key = hasher.newRecoveryKey();
        PasswordHasher.Hash pwd = hasher.hash(password);
        PasswordHasher.Hash rec = hasher.hash(PasswordHasher.canonicalKey(key));
        AppUser saved = tx.inTransaction(c -> {
            if (users.count() > 0) {
                throw ValidationException.of(FieldError.of(null, "auth.ownerExists"));
            }
            long id = users.insert(draft, pwd);
            storeRecovery(rec);
            AppUser u = draft.withIdentity(id, 0);
            audit.created(AUDIT_TYPE, id, users.snapshot(u));
            return u;
        });
        session.signIn(toSession(saved));
        return key;
    }

    private static List<FieldError> passwordErrors(String field, char[] password, char[] confirmation) {
        List<FieldError> errors = new ArrayList<>(UserRules.validatePassword(field, new String(password)));
        if (!java.util.Arrays.equals(password, confirmation)) {
            errors.add(FieldError.of("confirmation", "user.password.mismatch"));
        }
        return errors;
    }

    /** Changement de son propre mot de passe (obligatoire après une réinitialisation par le propriétaire). */
    public void changePassword(char[] current, char[] password, char[] confirmation) {
        UserSession.User me = session.currentUser();
        if (me.id() == null) {
            throw ValidationException.of(FieldError.of(null, "security.ownerOnly"));
        }
        List<FieldError> errors = new ArrayList<>(passwordErrors("password", password, confirmation));
        ValidationException.throwIfAny(errors);
        tx.runInTransaction(c -> {
            PasswordHasher.Hash stored = users.credentials(me.id()).orElseThrow();
            if (!hasher.verify(current, stored)) {
                throw ValidationException.of(FieldError.of("current", "auth.currentWrong"));
            }
            if (hasher.verify(password, stored)) {
                throw ValidationException.of(FieldError.of("password", "auth.samePassword"));
            }
            users.setPassword(me.id(), hasher.hash(password), false);
            audit.event(AUDIT_TYPE, me.id(), AuditAction.SECURITY, Map.of("event", "password_changed"), null);
        });
    }

    /**
     * Mot de passe oublié : la clé de récupération redéfinit le mot de passe d'un propriétaire. Renvoie la NOUVELLE
     * clé (l'ancienne ne sert qu'une fois).
     */
    public String resetWithRecoveryKey(String username, String recoveryKey, char[] password, char[] confirmation) {
        List<FieldError> errors = new ArrayList<>(passwordErrors("password", password, confirmation));
        ValidationException.throwIfAny(errors);
        String newKey = hasher.newRecoveryKey();
        PasswordHasher.Hash newRec = hasher.hash(PasswordHasher.canonicalKey(newKey));
        PasswordHasher.Hash pwd = hasher.hash(password);
        return tx.inTransaction(c -> {
            AppUser owner = users.findByUsername(username == null ? "" : username.strip())
                    .filter(u -> u.isOwner() && u.active())
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "auth.recoveryRefused")));
            Optional<PasswordHasher.Hash> stored = recovery();
            if (stored.isEmpty() || !hasher.verify(PasswordHasher.canonicalKey(recoveryKey), stored.get())) {
                throw ValidationException.of(FieldError.of(null, "auth.recoveryRefused"));
            }
            users.setPassword(owner.id(), pwd, false);
            storeRecovery(newRec);
            audit.event(AUDIT_TYPE, owner.id(), AuditAction.SECURITY, Map.of("event", "password_recovered"), null);
            return newKey;
        });
    }

    /** Nouvelle clé de récupération (propriétaire) ; l'ancienne cesse de fonctionner. */
    public String regenerateRecoveryKey() {
        session.requireOwner();
        String key = hasher.newRecoveryKey();
        PasswordHasher.Hash rec = hasher.hash(PasswordHasher.canonicalKey(key));
        tx.runInTransaction(c -> {
            storeRecovery(rec);
            audit.event(AUDIT_TYPE, session.currentUser().id() == null ? 0 : session.currentUser().id(),
                    AuditAction.SECURITY, Map.of("event", "recovery_key_regenerated"), null);
        });
        return key;
    }

    private void storeRecovery(PasswordHasher.Hash rec) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(RECOVERY_HASH, rec.hash());
        values.put(RECOVERY_SALT, rec.salt());
        values.put(RECOVERY_ITERATIONS, Integer.toString(rec.iterations()));
        settings.putAllSilently(values);
    }

    private Optional<PasswordHasher.Hash> recovery() {
        Optional<String> h = settings.get(RECOVERY_HASH);
        Optional<String> s = settings.get(RECOVERY_SALT);
        if (h.isEmpty() || s.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PasswordHasher.Hash(h.get(), s.get(),
                (int) settings.longValue(RECOVERY_ITERATIONS, PasswordHasher.ITERATIONS)));
    }
}
