package org.gu.fms.desktop.features.document;

import org.gu.fms.domain.document.FleetDocument;

import java.nio.file.Path;
import java.util.List;

/** Document saisi sur une fiche en création, avec les fichiers du poste à joindre. */
public record DocumentDraft(FleetDocument document, List<Path> files) {

    public DocumentDraft {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
