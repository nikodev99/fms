package org.gu.fms.desktop.platform.file;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.app.AppPaths;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Téléchargement d'un fichier de l'application vers le dossier « Téléchargements » de l'utilisateur, sans écraser un
 * fichier existant (« fiche (2).pdf »). Les méthodes font des entrées-sorties : à appeler hors du fil JavaFX.
 */
@Singleton
public final class Downloads {

    private final AppPaths paths;

    @Inject
    public Downloads(AppPaths paths) {
        this.paths = paths;
    }

    public Path directory() {
        return paths.downloadsDirectory();
    }

    /** Écrit le fichier et renvoie son chemin. */
    public Path save(String fileName, byte[] content) {
        try {
            Path dir = directory();
            Files.createDirectories(dir);
            String name = FileStore.cleanName(fileName, FileKind.detect(content).orElse(FileKind.PDF));
            int dot = name.lastIndexOf('.');
            String base = name.substring(0, dot);
            String ext = name.substring(dot);
            for (int i = 1; ; i++) {
                Path target = dir.resolve(i == 1 ? name : base + " (" + i + ")" + ext);
                try {
                    Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    return target;
                } catch (java.nio.file.FileAlreadyExistsException e) {
                    // nom pris : essayer le suivant
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Téléchargement impossible", e);
        }
    }

    /** Ouvre le fichier (ou le dossier) avec l'application du système ; faux si le système ne le permet pas. */
    public boolean open(Path path) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
                return true;
            }
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
