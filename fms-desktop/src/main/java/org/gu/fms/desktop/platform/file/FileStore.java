package org.gu.fms.desktop.platform.file;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Fichiers enregistrés dans la base (pièces jointes, rapports générés) : ils suivent les sauvegardes et ne sont
 * accessibles qu'à travers l'application. Un fichier ne se modifie jamais ; seul un fichier qui n'est plus référencé
 * se supprime (les clés étrangères protègent les autres).
 *
 * Les méthodes d'écriture s'appellent dans la transaction de l'opération qui rattache le fichier.
 */
@Singleton
public final class FileStore {

    /** Taille maximale d'un fichier enregistré : 10 Mo (un scan ou une photo de document). */
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    /** Taille maximale d'une photo choisie, avant allègement ({@link ImageOptimizer}). */
    public static final long MAX_PHOTO_BYTES = 30L * 1024 * 1024;
    public static final int NAME_MAX = 200;

    private static final String COLUMNS = "id, file_name, media_type, size_bytes, sha256, created_at, created_by";

    private final Jdbc jdbc;
    private final Clock clock;
    private final UserSession session;

    @Inject
    public FileStore(Jdbc jdbc, Clock clock, UserSession session) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.session = session;
    }

    /**
     * Lit un fichier du poste, allège les photos ({@link ImageOptimizer}) et contrôle le résultat (format, taille)
     * avant tout enregistrement. Une photo peut dépasser 10 Mo au départ (jusqu'à 30 Mo) : c'est le fichier allégé
     * qui compte.
     */
    public static byte[] read(Path source) {
        String name = source.getFileName().toString();
        try {
            if (Files.size(source) > MAX_PHOTO_BYTES) {
                throw ValidationException.of(FieldError.of(null, "file.tooLarge", name, MAX_PHOTO_BYTES / (1024 * 1024)));
            }
            byte[] content = Files.readAllBytes(source);
            FileKind kind = FileKind.detect(content)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "file.unsupported", name)));
            if (kind == FileKind.PDF && content.length > MAX_BYTES) {
                throw ValidationException.of(FieldError.of(null, "file.tooLarge", name, MAX_BYTES / (1024 * 1024)));
            }
            byte[] optimized = ImageOptimizer.optimize(content, kind);
            check(name, optimized);
            return optimized;
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible : " + source, e);
        }
    }

    public StoredFile put(String fileName, byte[] content) {
        FileKind kind = check(fileName, content);
        String name = cleanName(fileName, kind);
        String sha = sha256(content);
        String by = session.currentUser().displayName();
        var now = clock.instant();
        long id = jdbc.insert("INSERT INTO stored_file (file_name, media_type, size_bytes, sha256, content, created_at,"
                        + " created_by) VALUES (?, ?, ?, ?, ?, ?, ?)", name, kind.mediaType(), (long) content.length, sha,
                content, now, by);
        return new StoredFile(id, name, kind, content.length, sha, now, by);
    }

    public Optional<StoredFile> find(long id) {
        return jdbc.queryOne("SELECT " + COLUMNS + " FROM stored_file WHERE id = ?", mapper(""), id);
    }

    /** Contenu d'un fichier, contrôlé contre son empreinte (une altération de la base est signalée, pas affichée). */
    public byte[] content(long id) {
        record Content(byte[] bytes, String sha) {
        }
        Content c = jdbc.queryOne("SELECT content, sha256 FROM stored_file WHERE id = ?",
                        row -> new Content(row.bytes("content"), row.string("sha256")), id)
                .orElseThrow(() -> new IllegalStateException("Fichier introuvable : " + id));
        if (!sha256(c.bytes()).equals(c.sha())) {
            throw new IllegalStateException("Fichier altéré (empreinte différente) : " + id);
        }
        return c.bytes();
    }

    /** Supprime un fichier qui n'est plus référencé. */
    public void delete(long id) {
        jdbc.update("DELETE FROM stored_file WHERE id = ?", id);
    }

    /** Colonnes d'un fichier pour une jointure : {@code f.id AS f_id, …} (lues ensuite par {@code mapper("f_")}). */
    public static String columns(String tableAlias, String prefix) {
        StringBuilder sb = new StringBuilder();
        for (String c : COLUMNS.split(", ")) {
            sb.append(sb.isEmpty() ? "" : ", ").append(tableAlias).append('.').append(c).append(" AS ").append(prefix)
                    .append(c);
        }
        return sb.toString();
    }

    /** Lecture des colonnes d'un fichier, préfixées dans une jointure (voir {@link #columns}). */
    public static RowMapper<StoredFile> mapper(String alias) {
        return row -> new StoredFile(row.longValue(alias + "id"), row.string(alias + "file_name"),
                FileKind.ofMediaType(row.string(alias + "media_type")), row.longValue(alias + "size_bytes"),
                row.string(alias + "sha256"), row.instant(alias + "created_at"), row.string(alias + "created_by"));
    }

    private static FileKind check(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw ValidationException.of(FieldError.of(null, "file.empty", fileName));
        }
        if (content.length > MAX_BYTES) {
            throw ValidationException.of(FieldError.of(null, "file.tooLarge", fileName, MAX_BYTES / (1024 * 1024)));
        }
        return FileKind.detect(content)
                .orElseThrow(() -> ValidationException.of(FieldError.of(null, "file.unsupported", fileName)));
    }

    /** Nom sans chemin, extension conforme au contenu, longueur bornée. */
    static String cleanName(String fileName, FileKind kind) {
        String base = fileName == null ? "" : fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).strip();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_");
        if (base.isBlank()) {
            base = "fichier";
        }
        int max = NAME_MAX - kind.extension().length() - 1;
        if (base.length() > max) {
            base = base.substring(0, max);
        }
        return base + "." + kind.extension();
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
