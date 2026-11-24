package org.gu.fms.desktop.features.report;

/** Les rapports du poste ; le libellé est « report.kind.&lt;NOM&gt; ». */
public enum ReportKind {
    ACCOUNT_STATEMENT("REL", "releve"),
    VEHICLE_SHEET("VEH", "fiche"),
    MONTHLY_CLOSINGS("CLO", "clotures"),
    MONTHLY_COLLECTIONS("VER", "versements");

    private final String prefix;
    private final String fileStem;

    ReportKind(String prefix, String fileStem) {
        this.prefix = prefix;
        this.fileStem = fileStem;
    }

    /** Préfixe de la référence (REL-20261123-101500). */
    public String prefix() {
        return prefix;
    }

    /** Début du nom de fichier (releve-caisse.pdf). */
    public String fileStem() {
        return fileStem;
    }
}
