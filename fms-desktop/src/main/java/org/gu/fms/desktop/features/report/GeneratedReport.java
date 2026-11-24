package org.gu.fms.desktop.features.report;

import org.gu.fms.desktop.platform.file.StoredFile;

import java.time.Instant;

/**
 * Rapport généré, conservé tel quel dans l'historique.
 *
 * @param subject objet du rapport : compte et période, véhicule, mois
 */
public record GeneratedReport(long id, ReportKind kind, String reference, String subject, StoredFile file,
                              Instant generatedAt, String generatedBy) {
}