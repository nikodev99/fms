package org.gu.fms.desktop.features.document;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.platform.db.Jdbc;
import org.gu.fms.desktop.platform.db.RowMapper;
import org.gu.fms.desktop.platform.repository.JdbcRepository;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.document.DocumentType.DocumentSubject;
import org.gu.fms.domain.document.FleetDocument;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public final class FleetDocumentRepository extends JdbcRepository<FleetDocument> {

    @Inject
    public FleetDocumentRepository(Jdbc jdbc, Clock clock) {
        super(jdbc, clock);
    }

    @Override
    protected String table() {
        return "fleet_document";
    }

    @Override
    protected String defaultOrder() {
        return "subject, COALESCE(vehicle_id, driver_id), type_id, id";
    }

    @Override
    protected RowMapper<FleetDocument> mapper() {
        return row -> new FleetDocument(row.longValue("id"), row.longValue("version"), row.longValue("type_id"),
                row.enumValue("subject", DocumentType.DocumentSubject.class), row.longOrNull("vehicle_id"),
                row.longOrNull("driver_id"), row.string("number"), row.string("issuer"), row.localDate("issued_on"),
                row.localDate("expires_on"), row.longOrNull("replaces_id"), row.bool("superseded"),
                row.string("notes"));
    }

    @Override
    protected Long idOf(FleetDocument d) {
        return d.id();
    }

    @Override
    protected long versionOf(FleetDocument d) {
        return d.version();
    }

    @Override
    protected Map<String, Object> columnsOf(FleetDocument d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type_id", d.typeId());
        m.put("subject", d.subject());
        m.put("vehicle_id", d.vehicleId());
        m.put("driver_id", d.driverId());
        m.put("number", d.number());
        m.put("issuer", d.issuer());
        m.put("issued_on", d.issuedOn());
        m.put("expires_on", d.expiresOn());
        m.put("replaces_id", d.replacesId());
        m.put("superseded", d.superseded());
        m.put("notes", d.notes());
        return m;
    }

    /** Documents en cours (non remplacés). */
    public List<FleetDocument> findCurrent() {
        return jdbc.query("SELECT * FROM fleet_document WHERE superseded = 0 ORDER BY " + defaultOrder(),
                mapper());
    }

    public Optional<FleetDocument> findCurrent(long typeId, DocumentType.DocumentSubject subject, long ownerId) {
        String column = subject == DocumentSubject.VEHICLE ? "vehicle_id" : "driver_id";
        return jdbc.queryOne("SELECT * FROM fleet_document WHERE superseded = 0 AND type_id = ? AND "
                + column + " = ?", mapper(), typeId, ownerId);
    }

    /** Historique d'un titulaire pour un type, du plus récent au plus ancien. */
    public List<FleetDocument> history(long typeId, DocumentSubject subject, long ownerId) {
        String column = subject == DocumentSubject.VEHICLE ? "vehicle_id" : "driver_id";
        return jdbc.query("SELECT * FROM fleet_document WHERE type_id = ? AND " + column
                + " = ? ORDER BY COALESCE(expires_on, issued_on, created_at) DESC, id DESC", mapper(), typeId, ownerId);
    }

    /** Documents d'un titulaire (tous types, historique compris). */
    public List<FleetDocument> forOwner(DocumentSubject subject, long ownerId) {
        String column = subject == DocumentSubject.VEHICLE ? "vehicle_id" : "driver_id";
        return jdbc.query("SELECT * FROM fleet_document WHERE " + column
                + " = ? ORDER BY type_id, COALESCE(expires_on, issued_on, created_at) DESC, id DESC", mapper(), ownerId);
    }

    /** Marque un document comme remplacé ({@code true}) ou le rétablit comme document en cours ({@code false}). */
    public void setSuperseded(long id, boolean superseded) {
        jdbc.update("UPDATE fleet_document SET superseded = ?, version = version + 1, updated_at = ? WHERE id = ?",
                superseded, clock.instant(), id);
    }
}
