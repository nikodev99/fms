package org.gu.fms.desktop.features.security;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.DataAccessException;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.security.PasswordHasher;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.security.AppUser;
import org.gu.fms.domain.security.UserRole;
import org.gu.fms.domain.security.UserRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gestion des utilisateurs (Paramètres › Utilisateurs), réservée au propriétaire : création avec un mot de passe
 * provisoire (à changer à la première connexion), modules ouverts, désactivation, réinitialisation du mot de passe.
 * Un utilisateur n'est jamais supprimé (ses traces d'audit le nomment) ; il reste toujours un propriétaire actif.
 */
@Singleton
public final class UserService implements CrudService<AppUser> {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final UserSession session;
    private final TransactionManager tx;
    private final AuditLog audit;

    @Inject
    public UserService(UserRepository users, PasswordHasher hasher, UserSession session, TransactionManager tx, AuditLog audit) {
        this.users = users;
        this.hasher = hasher;
        this.session = session;
        this.tx = tx;
        this.audit = audit;
    }

    @Override
    public List<AppUser> findAll() {
        session.requireOwner();
        return users.findAll();
    }

    @Override
    public AppUser save(AppUser user) {
        return update(user);
    }

    /** Création avec mot de passe provisoire : l'utilisateur devra le changer à sa première connexion. */
    public AppUser create(AppUser input, char[] temporaryPassword) {
        session.requireOwner();
        AppUser u = prepare(UserRules.normalize(input));
        List<FieldError> errors = new ArrayList<>(UserRules.validate(u));
        errors.addAll(UserRules.validatePassword("password", new String(temporaryPassword)));
        ValidationException.throwIfAny(errors);
        PasswordHasher.Hash hash = hasher.hash(temporaryPassword);
        try {
            return tx.inTransaction(c -> {
                if (users.findByUsername(u.username()).isPresent()) {
                    throw ValidationException.of(FieldError.of("username", "user.username.taken", u.username()));
                }
                AppUser ready = new AppUser(null, 0, u.username(), u.displayName(), u.role(), true, true, 0, null,
                        null, u.modules());
                long id = users.insert(ready, hash);
                users.replaceModules(id, ready.modules());
                AppUser saved = ready.withIdentity(id, 0);
                audit.created(AuthService.AUDIT_TYPE, id, users.snapshot(saved));
                return saved;
            });
        } catch (DataAccessException e) {
            if (e.isUniqueViolation()) {
                throw ValidationException.of(FieldError.of("username", "user.username.taken", u.username()));
            }
            throw e;
        }
    }

    /** Le propriétaire n'a pas de liste de modules (accès complet). */
    private static AppUser prepare(AppUser u) {
        return u.role() == UserRole.OWNER ? new AppUser(u.id(), u.version(), u.username(), u.displayName(), u.role(),
                u.active(), u.mustChangePassword(), u.failedAttempts(), u.lockedUntil(), u.lastLoginAt(), Set.of()) : u;
    }

    public AppUser update(AppUser input) {
        session.requireOwner();
        AppUser u = prepare(UserRules.normalize(input));
        ValidationException.throwIfAny(UserRules.validate(u));
        try {
            return tx.inTransaction(c -> {
                AppUser before = users.findById(u.id())
                        .orElseThrow(() -> new StaleDataException(AuthService.AUDIT_TYPE, u.id(), u.version()));
                if (!before.username().equals(u.username())
                        && users.findByUsername(u.username()).filter(o -> !o.id().equals(u.id())).isPresent()) {
                    throw ValidationException.of(FieldError.of("username", "user.username.taken", u.username()));
                }
                boolean losesOwner = before.isOwner() && before.active() && (!u.isOwner() || !u.active());
                if (losesOwner && users.countActiveOwners() <= 1) {
                    throw ValidationException.of(FieldError.of(null, "user.lastOwner"));
                }
                if (u.id().equals(session.currentUser().id()) && (!u.active() || !u.isOwner())) {
                    throw ValidationException.of(FieldError.of(null, "user.notYourself"));
                }
                AppUser ready = new AppUser(u.id(), u.version(), u.username(), u.displayName(), u.role(), u.active(),
                        before.mustChangePassword(), before.failedAttempts(), before.lockedUntil(), before.lastLoginAt(),
                        u.modules());
                long version = users.update(ready);
                users.replaceModules(ready.id(), ready.modules());
                audit.updated(AuthService.AUDIT_TYPE, ready.id(), users.snapshot(before), users.snapshot(ready), null);
                return ready.withIdentity(ready.id(), version);
            });
        } catch (DataAccessException e) {
            if (e.isUniqueViolation()) {
                throw ValidationException.of(FieldError.of("username", "user.username.taken", u.username()));
            }
            throw e;
        }
    }

    /** Nouveau mot de passe provisoire, à changer à la prochaine connexion ; le verrouillage est levé. */
    public void resetPassword(long userId, char[] temporaryPassword) {
        session.requireOwner();
        ValidationException.throwIfAny(UserRules.validatePassword("password", new String(temporaryPassword)));
        PasswordHasher.Hash hash = hasher.hash(temporaryPassword);
        tx.runInTransaction(c -> {
            users.findById(userId).orElseThrow(() -> new StaleDataException(AuthService.AUDIT_TYPE, userId, 0));
            users.setPassword(userId, hash, true);
            audit.event(AuthService.AUDIT_TYPE, userId, AuditAction.SECURITY, Map.of("event", "password_reset"), null);
        });
    }
}
