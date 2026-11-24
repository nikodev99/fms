package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.app.AppPaths;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.file.FileKind;
import org.gu.fms.desktop.platform.file.FileStore;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.company.CompanyProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Identité de l'entreprise. Le logo choisi est enregistré dans la base ({@link FileStore}) : il suit les sauvegardes
 * et la restauration, et reste disponible si le fichier d'origine est déplacé ou supprimé. Le réglage
 * « company.logoFile » porte l'identifiant du fichier enregistré.
 *
 * Reprise : un logo des versions précédentes (fichier du dossier « branding » des données) est enregistré dans la
 * base à la première lecture ; le fichier d'origine n'est pas supprimé.
 */
@Singleton
public final class CompanyService {

    public static final long LOGO_MAX_BYTES = 2L * 1024 * 1024;

    private static final String PREFIX = "company.";
    private static final String LOGO_KEY = "company.logoFile";

    private final SettingsStore store;
    private final AppPaths paths;
    private final FileStore files;
    private final TransactionManager tx;

    @Inject
    public CompanyService(SettingsStore store, AppPaths paths, FileStore files, TransactionManager tx) {
        this.store = store;
        this.paths = paths;
        this.files = files;
        this.tx = tx;
    }

    public CompanyProfile load() {
        Map<String, String> v = store.byPrefix(PREFIX);
        String logo = v.get(LOGO_KEY);
        if (logo != null && fileId(logo) == null) {
            logo = importLegacyLogo(logo);
        }
        return new CompanyProfile(v.get("company.name"), v.get("company.legalForm"), v.get("company.rccm"),
                v.get("company.niu"), v.get("company.address"), v.get("company.city"), v.get("company.phone"),
                v.get("company.email"), logo);
    }

    /**
     * @param newLogo fichier logo choisi par l'utilisateur (null = inchangé, ou retiré si {@code input} n'a plus
     *                de logo)
     */
    public CompanyProfile save(CompanyProfile input, Path newLogo) {
        CompanyProfile normalized = input.normalize();
        ValidationException.throwIfAny(normalized.validate());
        byte[] logoContent = newLogo == null ? null : readLogo(newLogo);
        return tx.inTransaction(c -> {
            String previous = store.get(LOGO_KEY).orElse(null);
            CompanyProfile profile = normalized;
            if (logoContent != null) {
                long id = files.put(newLogo.getFileName().toString(), logoContent).id();
                profile = profile.withLogo(Long.toString(id));
            }
            Map<String, String> values = new LinkedHashMap<>();
            values.put("company.name", profile.name());
            values.put("company.legalForm", profile.legalForm());
            values.put("company.rccm", profile.rccm());
            values.put("company.niu", profile.niu());
            values.put("company.address", profile.address());
            values.put("company.city", profile.city());
            values.put("company.phone", profile.phone());
            values.put("company.email", profile.email());
            values.put(LOGO_KEY, profile.logoFile());
            store.putAll(values, null);
            Long old = previous == null ? null : fileId(previous);
            if (old != null && !previous.equals(profile.logoFile())) {
                files.delete(old);              // logo remplacé ou retiré : plus référencé
            }
            return profile;
        });
    }

    /** Image du logo enregistré, ou vide (pas de logo, ou fichier d'une ancienne version introuvable). */
    public Optional<byte[]> logo(CompanyProfile profile) {
        Long id = profile.logoFile() == null ? null : fileId(profile.logoFile());
        return id == null ? Optional.empty() : Optional.of(files.content(id));
    }

    private static byte[] readLogo(Path source) {
        try {
            if (Files.size(source) > LOGO_MAX_BYTES) {
                throw ValidationException.of(FieldError.of(null, "company.logo.tooLarge"));
            }
            byte[] content = Files.readAllBytes(source);
            if (!FileKind.detect(content).map(FileKind::isImage).orElse(false)) {
                throw ValidationException.of(FieldError.of(null, "company.logo.format"));
            }
            return content;
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du logo impossible", e);
        }
    }

    /** Logo d'une version précédente (nom de fichier du dossier « branding ») : enregistré dans la base. */
    private String importLegacyLogo(String fileName) {
        Path legacy = paths.dataDirectory().resolve("branding").resolve(fileName);
        if (!Files.isRegularFile(legacy)) {
            return null;
        }
        try {
            byte[] content = readLogo(legacy);
            return tx.inTransaction(c -> {
                String id = Long.toString(files.put(fileName, content).id());
                store.putAllSilently(Map.of(LOGO_KEY, id));
                return id;
            });
        } catch (ValidationException | UncheckedIOException e) {
            return null;            // fichier illisible ou non conforme : logo FMS en attendant un nouveau choix
        }
    }

    private static Long fileId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}