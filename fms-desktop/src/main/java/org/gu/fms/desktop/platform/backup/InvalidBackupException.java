package org.gu.fms.desktop.platform.backup;

/** Fichier refusé pour une restauration ; {@link #getMessage()} est une clé de message. */
public class InvalidBackupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidBackupException(String messageKey) {
        super(messageKey);
    }

    public String messageKey() {
        return getMessage();
    }
}
