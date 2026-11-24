package org.gu.fms.desktop.platform.db;

import java.sql.SQLException;

/** Erreur d'accès aux données. Encapsule {@link SQLException} pour ne pas l'imposer aux couches supérieures. */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public static DataAccessException wrap(SQLException e) {
        return new DataAccessException(e.getMessage(), e);
    }

    /** Violation d'une contrainte UNIQUE (ex. immatriculation déjà enregistrée). */
    public boolean isUniqueViolation() {
        return causeMessageContains("UNIQUE constraint failed");
    }

    /** Violation d'une contrainte CHECK ou FOREIGN KEY : la base a refusé une donnée incohérente. */
    public boolean isIntegrityViolation() {
        return causeMessageContains("CHECK constraint failed") || causeMessageContains("FOREIGN KEY constraint failed");
    }

    /** La ligne est encore référencée (ex. catégorie utilisée par un véhicule) : suppression refusée. */
    public boolean isForeignKeyViolation() {
        return causeMessageContains("FOREIGN KEY constraint failed");
    }

    private boolean causeMessageContains(String text) {
        for (Throwable t = this; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }
}
