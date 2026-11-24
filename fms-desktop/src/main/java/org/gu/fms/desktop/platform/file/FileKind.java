package org.gu.fms.desktop.platform.file;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formats de fichier acceptés (pièces jointes, rapports). Le format est reconnu par le contenu (signature), pas par
 * l'extension : un fichier renommé en « .pdf » n'est pas accepté pour autant.
 */
public enum FileKind {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");

    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final String mediaType;
    private final String extension;

    FileKind(String mediaType, String extension) {
        this.mediaType = mediaType;
        this.extension = extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public String extension() {
        return extension;
    }

    public boolean isImage() {
        return this != PDF;
    }

    public static Optional<FileKind> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, PDF_SIGNATURE)) {
            return Optional.of(PDF);
        }
        if (startsWith(content, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        return Optional.empty();
    }

    public static FileKind ofMediaType(String mediaType) {
        return Arrays.stream(values()).filter(k -> k.mediaType.equals(mediaType)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type de fichier inconnu : " + mediaType));
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
