package org.gu.fms.desktop.platform.brand;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logo FMS livré avec l'application : un seul endroit pour l'icône de fenêtre, la barre latérale, l'écran de
 * connexion et l'en-tête des rapports (quand l'entreprise n'a pas de logo).
 *
 * Fichiers attendus dans {@code src/main/resources/com/globalunderscore/fms/desktop/images/} :
 * {@code fms-logo-16.png}, {@code -24}, {@code -32}, {@code -48}, {@code -64}, {@code -128}, {@code -256}, et/ou
 * {@code fms-logo.png} (une seule grande image suffit : elle sert pour toutes les tailles). Si aucun n'est trouvé,
 * chaque écran garde son monogramme « FMS » et le journal indique le chemin cherché.
 */
public final class BrandAssets {

    public static final String FOLDER = "/org/gu/fms/desktop/images/";
    public static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final String GENERIC = "fms-logo.png";

    private static final System.Logger LOG = System.getLogger(BrandAssets.class.getName());
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final ConcurrentHashMap<Integer, Optional<byte[]>> CACHE = new ConcurrentHashMap<>();

    private BrandAssets() {
    }

    /**
     * Logo d'au moins {@code size} pixels si possible : la taille demandée, sinon la plus proche au-dessus, sinon
     * {@code fms-logo.png}, sinon la plus grande disponible. Vide si aucun fichier n'est livré.
     */
    public static Optional<byte[]> logo(int size) {
        return CACHE.computeIfAbsent(size, BrandAssets::find).map(byte[]::clone);
    }

    /** Fichier livré pour exactement cette taille (icônes de fenêtre), sans repli. */
    public static Optional<byte[]> exact(int size) {
        return read(FOLDER + "fms-logo-" + size + ".png");
    }

    private static Optional<byte[]> find(int size) {
        for (int s : SIZES) {
            if (s >= size) {
                Optional<byte[]> found = exact(s);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        Optional<byte[]> generic = read(FOLDER + GENERIC);
        if (generic.isPresent()) {
            return generic;
        }
        for (int i = SIZES.length - 1; i >= 0; i--) {
            Optional<byte[]> found = exact(SIZES[i]);
            if (found.isPresent()) {
                return found;
            }
        }
        if (WARNED.compareAndSet(false, true)) {
            LOG.log(System.Logger.Level.WARNING, "Logo FMS introuvable dans le classpath : "
                    + FOLDER + "fms-logo-<taille>.png ou " + FOLDER + GENERIC + " — monogramme affiché.");
        }
        return Optional.empty();
    }

    private static Optional<byte[]> read(String resource) {
        try (InputStream in = BrandAssets.class.getResourceAsStream(resource)) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture impossible : " + resource, e);
        }
    }
}