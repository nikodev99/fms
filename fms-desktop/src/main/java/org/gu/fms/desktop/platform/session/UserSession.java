package org.gu.fms.desktop.platform.session;

import jakarta.inject.Singleton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;

import java.util.Objects;
import java.util.Set;

/**
 * Utilisateur connecté, ses droits, et la garde des opérations réservées au propriétaire.
 * <p>
 * Hors session (avant la connexion, pendant la mise à niveau de la base), {@link #currentUser()} renvoie l'utilisateur
 * « Système » sans aucun droit : les traces d'audit restent attribuées, aucune opération réservée n'est possible.
 * Le propriétaire a accès à tous les modules ; un utilisateur seulement à ceux que le propriétaire lui a ouverts.
 */
@Singleton
public final class UserSession {

    public static final User SYSTEM = new User(null, "system", "Système", Role.USER, Set.of(), false);

    /** Valeur de référence, lisible depuis n'importe quel fil (services en tâche de fond). */
    private volatile User user = SYSTEM;
    /** Copie observable, mise à jour sur le fil JavaFX (la coque l'écoute pour reconstruire le menu). */
    private final ReadOnlyObjectWrapper<User> current = new ReadOnlyObjectWrapper<>(this, "current", SYSTEM);

    /** Utilisateur connecté ; {@link #SYSTEM} hors session. Jamais null. */
    public User currentUser() {
        return user;
    }

    public ReadOnlyObjectProperty<User> currentUserProperty() {
        return current.getReadOnlyProperty();
    }

    public boolean isSignedIn() {
        return user != SYSTEM;
    }

    public void signIn(User newUser) {
        set(Objects.requireNonNull(newUser, "user"));
    }

    public void signOut() {
        set(SYSTEM);
    }

    private void set(User value) {
        this.user = value;
        if (javafx.application.Platform.isFxApplicationThread()) {
            current.set(value);
            return;
        }
        try {
            javafx.application.Platform.runLater(() -> current.set(user));
        } catch (IllegalStateException toolkitNotStarted) {
            current.set(value);                       // tests sans interface
        }
    }

    public boolean isOwner() {
        return isSignedIn() && user.role() == Role.OWNER;
    }

    /** Accès à un module (identifiant de route). Paramètres : propriétaire seulement. */
    public boolean canAccess(String module) {
        User u = user;
        if (!isSignedIn()) {
            return false;
        }
        return u.role() == Role.OWNER || (!OWNER_ONLY_MODULES.contains(module) && u.modules().contains(module));
    }

    /** Modules jamais ouverts à un simple utilisateur. */
    public static final Set<String> OWNER_ONLY_MODULES = Set.of("settings");

    /**
     * Opération réservée au propriétaire (contre-passation, réouverture de clôture, validation et paiement de la paie,
     * utilisateurs, paramètres, restauration).
     *
     * @throws ValidationException sinon
     */
    public void requireOwner() {
        if (!isOwner()) {
            throw ValidationException.of(FieldError.of(null, "security.ownerOnly"));
        }
    }

    public enum Role {
        OWNER,
        USER
    }

    /**
     * @param id        identifiant en base ; null pour {@link #SYSTEM} et la connexion initiale
     * @param modules   modules ouverts (sans objet pour le propriétaire)
     * @param bootstrap connexion avec les identifiants par défaut, tant qu'aucun utilisateur n'existe : seule la
     *                  création du propriétaire est alors possible
     */
    public record User(Long id, String login, String displayName, Role role, Set<String> modules, boolean bootstrap) {
        public User {
            Objects.requireNonNull(login, "login");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(role, "role");
            modules = modules == null ? Set.of() : Set.copyOf(modules);
        }

        public static User owner(Long id, String login, String displayName) {
            return new User(id, login, displayName, Role.OWNER, Set.of(), false);
        }

        public String initials() {
            if (displayName.isBlank()) {
                return "?";
            }
            String[] parts = displayName.strip().split("\\s+");
            String first = parts[0].substring(0, 1);
            String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
            return (first + second).toUpperCase();
        }
    }
}
