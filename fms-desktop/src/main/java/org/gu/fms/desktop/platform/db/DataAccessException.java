package org.gu.fms.desktop.platform.db;

import java.sql.SQLException;

public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String s, Throwable ex) {
        super(s, ex);
    }

    public static DataAccessException wrap(SQLException ex) {
        return new DataAccessException(ex.getMessage(), ex);
    }

    /** Violation d'une contrainte UNIQUE (ex. immatriculation déjà enregistrée). */
    public boolean isUniqueViolation() {
        return causeMessageContains("UNIQUE constraint failed");
    }

    /** Violation d'une contrainte CHECK ou FOREIGN KEY : la base a refusé une donnée incohérente. */
    public boolean isIntegrityViolation() {
        return causeMessageContains("CHECK constraint failed") || causeMessageContains("FOREIGN KEY constraint failed");
    }

    private boolean causeMessageContains(String text) {
        for (Throwable t = this; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(text))
                return true;
        }
        return false;
    }
}
