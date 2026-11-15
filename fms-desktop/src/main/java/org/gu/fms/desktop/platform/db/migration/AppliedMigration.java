package org.gu.fms.desktop.platform.db.migration;

public record AppliedMigration(
        int version,
        String script,
        String checksum
) {
}
