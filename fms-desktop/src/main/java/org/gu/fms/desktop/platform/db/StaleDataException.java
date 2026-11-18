package org.gu.fms.desktop.platform.db;

public class StaleDataException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StaleDataException(String table, long id, long expectedVersion) {
        super("Enregistrement " + table + "#" + id + " modifié depuis sa lecture (version attendue "
                + expectedVersion + ")");
    }
}
