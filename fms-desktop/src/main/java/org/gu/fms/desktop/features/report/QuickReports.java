package org.gu.fms.desktop.features.report;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.property.BooleanProperty;
import org.gu.fms.desktop.platform.ui.preview.Previews;

import java.time.YearMonth;

/**
 * Rapports téléchargés directement depuis leur module, sans aperçu ni paramètre à saisir : la fiche du véhicule
 * affiché, le rapport des versements du mois affiché. Ils rejoignent l'historique du module Rapports.
 */
@Singleton
public final class QuickReports {

    private final ReportService reports;
    private final Previews previews;

    @Inject
    public QuickReports(ReportService reports, Previews previews) {
        this.reports = reports;
        this.previews = previews;
    }

    public void vehicleSheet(long vehicleId, BooleanProperty busy) {
        previews.download(() -> named(reports.vehicleSheet(vehicleId)), busy);
    }

    public void monthlyCollections(YearMonth month, BooleanProperty busy) {
        previews.download(() -> named(reports.monthlyCollections(month)), busy);
    }

    private Previews.Named named(GeneratedReport report) {
        return new Previews.Named(report.file().fileName(), reports.content(report));
    }
}
