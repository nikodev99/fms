package org.gu.fms.desktop.platform.db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Migration(
        int version,
        String description,
        String resource,
        String sql,
        String checksum
) {
    private static final Pattern NAME = Pattern.compile("V(\\d{1,6})__([A-Za-z0-9_]+)\\.sql");

    public static Migration fromResource(String resource, String sql) {
        String fileName = resource.substring(resource.lastIndexOf('/') + 1);
        Matcher m = NAME.matcher(fileName);
        if (!m.matches()) {
            throw new MigrationException("Nom de migration invalide : " + fileName
                    + " (attendu : V001__description.sql)");
        }
        String normalized = sql.replace("\r\n", "\n").replace('\r', '\n');
        return new Migration(Integer.parseInt(m.group(1)), m.group(2).replace('_', ' '), fileName,
                normalized, sha256(normalized));
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
