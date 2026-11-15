package org.gu.fms.desktop.platform.db.migration;

public class MigrationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MigrationException(String message) {
        super(message);
    }

    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
