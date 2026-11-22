package org.gu.fms.desktop.platform.backup;

/** Origine d'une sauvegarde, lue dans le nom du fichier. */
public enum BackupKind {
    MANUAL,
    AUTOMATIC,
    BEFORE_UPGRADE,
    BEFORE_RESTORE,
    OTHER
}
