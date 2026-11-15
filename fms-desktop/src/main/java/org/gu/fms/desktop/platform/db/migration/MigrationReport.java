package org.gu.fms.desktop.platform.db.migration;

import java.nio.file.Path;
import java.util.List;

/** Résultat d'une exécution. {@code backup} est nul si aucune sauvegarde n'était nécessaire. */
public record MigrationReport(
        int fromVersion,
        int toVersion,
        List<Migration> applied,
        Path backup
) {
}
