package org.gu.fms.domain.security;

public enum UserRole {
    /** Accès à tous les modules et aux opérations sensibles. */
    OWNER,
    /** Accès aux seuls modules ouverts par le propriétaire. */
    USER
}
