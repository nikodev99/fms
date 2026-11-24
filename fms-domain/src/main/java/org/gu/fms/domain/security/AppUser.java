package org.gu.fms.domain.security;

import java.time.Instant;
import java.util.Set;

/**
 * Utilisateur de l'application (le mot de passe n'est jamais dans cet objet : seule son empreinte est stockée, par le
 * dépôt). Le propriétaire a accès à tout ; un utilisateur seulement aux modules listés.
 */
public record AppUser(Long id, long version, String username, String displayName, UserRole role, boolean active,
                      boolean mustChangePassword, int failedAttempts, Instant lockedUntil, Instant lastLoginAt,
                      Set<String> modules) {

    public AppUser {
        modules = modules == null ? Set.of() : Set.copyOf(modules);
    }

    public boolean isOwner() {
        return role == UserRole.OWNER;
    }

    public AppUser withIdentity(long newId, long newVersion) {
        return new AppUser(newId, newVersion, username, displayName, role, active, mustChangePassword, failedAttempts,
                lockedUntil, lastLoginAt, modules);
    }
}
