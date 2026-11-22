package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.app.AppPaths;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.company.CompanyProfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Identité de l'entreprise. Le logo choisi est COPIÉ dans le dossier de données (sous-dossier « branding ») :
 * il reste disponible même si le fichier d'origine est déplacé ou supprimé. Les anciens logos sont conservés.
 */
@Singleton
public final class CompanyService {

    public static final Set<String> LOGO_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    public static final long LOGO_MAX_BYTES = 2L * 1024 * 1024;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final String PREFIX = "company.";

    private final SettingsStore store;
    private final AppPaths paths;
    private final Clock clock;

    @Inject
    public CompanyService(SettingsStore store, AppPaths paths, Clock clock) {
        this.store = store;
        this.paths = paths;
        this.clock = clock;
    }

    public CompanyProfile load() {
        Map<String, String> v = store.byPrefix(PREFIX);
        return new CompanyProfile(v.get("company.name"), v.get("company.legalForm"), v.get("company.rccm"),
                v.get("company.niu"), v.get("company.address"), v.get("company.city"), v.get("company.phone"),
                v.get("company.email"), v.get("company.logoFile"));
    }

    /**
     * @param newLogo fichier logo choisi par l'utilisateur (null = inchangé)
     */
    public CompanyProfile save(CompanyProfile input, Path newLogo) {
        CompanyProfile profile = input.normalize();
        ValidationException.throwIfAny(profile.validate());
        if (newLogo != null) {
            profile = profile.withLogo(copyLogo(newLogo));
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
        values.put("company.logoFile", profile.logoFile());
        store.putAll(values, null);
        return profile;
    }

    public Path brandingDirectory() {
        return paths.dataDirectory().resolve("branding");
    }

    /** Chemin complet du logo enregistré, ou null. */
    public Path logoPath(CompanyProfile profile) {
        return profile.logoFile() == null ? null : brandingDirectory().resolve(profile.logoFile());
    }

    private String copyLogo(Path source) {
        String name = source.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!LOGO_EXTENSIONS.contains(ext)) {
            throw ValidationException.of(FieldError.of(null, "company.logo.format"));
        }
        try {
            if (Files.size(source) > LOGO_MAX_BYTES) {
                throw ValidationException.of(FieldError.of(null, "company.logo.tooLarge"));
            }
            Files.createDirectories(brandingDirectory());
            String target = "logo-" + STAMP.format(clock.instant()) + "." + ext;
            Files.copy(source, brandingDirectory().resolve(target));
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Copie du logo impossible", e);
        }
    }
}
