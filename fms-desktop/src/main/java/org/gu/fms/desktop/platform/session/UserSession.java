package org.gu.fms.desktop.platform.session;

import jakarta.inject.Singleton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.util.Objects;

/**
 * Utilisateur connecté.
 *
 * Provisoire : tant que le module Utilisateurs et rôles n'existe pas, la session démarre avec
 * le propriétaire local. Toutes les traces d'audit passent déjà par ici, donc l'arrivée de
 * l'authentification ne changera rien pour les autres modules.
 */
@Singleton
public final class UserSession {
    private final ReadOnlyObjectWrapper<User> current = new ReadOnlyObjectWrapper<>(this, "current", new User("local", "Utilisateur local", Role.OWNER));

    public User currentUser() {
        return current.get();
    }

    public ReadOnlyObjectProperty<User> currentUserProperty() {
        return current.getReadOnlyProperty();
    }

    public enum Role {
        OWNER,
        CASHIER,
        ACCOUNTANT,
        FLEET_MANAGER,
    }

    public record User(String username, String displayName, Role role) {
        public User {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(role, "role");
        }

        public String initials() {
            return displayName.isBlank() ? "?" : displayName.substring(0, 1).toUpperCase();
        }
    }
}
