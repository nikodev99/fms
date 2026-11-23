package org.gu.fms.desktop.features.collection;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.CategoryRepository;
import org.gu.fms.desktop.features.finance.Ledger;
import org.gu.fms.desktop.features.finance.TransactionRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.audit.AuditAction;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.db.StaleDataException;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.services.CrudService;
import org.gu.fms.domain.collection.*;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.finance.FinancialTransaction;
import org.gu.fms.domain.finance.SystemCategory;
import org.gu.fms.domain.finance.TransactionDirection;
import org.gu.fms.domain.finance.TransactionSource;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Versements des chauffeurs (instructions §4.3).
 *
 * <ul>
 *   <li>Création : versement + entrée de trésorerie liée, dans la même transaction SQL.</li>
 *   <li>Modification : seulement le versement du jour, ni pointé ni clôturé ; motif obligatoire ; la transaction
 *       liée suit (montant, compte, véhicule, chauffeur).</li>
 *   <li>Avoir : annule un versement en totalité, daté du jour ; sa transaction est contre-passée.</li>
 *   <li>Versement ou avoir sur une journée déjà pointée pour ce chauffeur : régularisation au compte chauffeur.</li>
 * </ul>
 */
@Singleton
public final class CollectionService implements CrudService<CollectionEntry> {

    static final String AUDIT_TYPE = "collection";

    private final CollectionRepository collections;
    private final DailyPointRepository points;
    private final DriverRepository drivers;
    private final VehicleRepository vehicles;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final Ledger ledger;
    private final DriverLedgerService driverLedger;
    private final TransactionManager tx;
    private final AuditLog audit;
    private final Clock clock;
    private final Formats formats;

    @Inject
    public CollectionService(CollectionRepository collections, DailyPointRepository points, DriverRepository drivers,
                             VehicleRepository vehicles, CategoryRepository categories,
                             TransactionRepository transactions, Ledger ledger, DriverLedgerService driverLedger,
                             TransactionManager tx, AuditLog audit, Clock clock, Formats formats) {
        this.collections = collections;
        this.points = points;
        this.drivers = drivers;
        this.vehicles = vehicles;
        this.categories = categories;
        this.transactions = transactions;
        this.ledger = ledger;
        this.driverLedger = driverLedger;
        this.tx = tx;
        this.audit = audit;
        this.clock = clock;
        this.formats = formats;
    }

    @Override
    public List<CollectionEntry> findAll() {
        return collections.findAll();
    }

    public List<CollectionEntry> findBetween(LocalDate from, LocalDate to) {
        return collections.findBetween(from, to);
    }

    /** Pourquoi ce versement ne se modifie-t-il pas directement ? null = modifiable aujourd'hui. */
    public String lockReason(CollectionEntry c) {
        LocalDate locked = ledger.lockedThrough(c.accountId());
        return CollectionRules.lockReason(c, ledger.today(), points.isPointed(c.driverId(), c.date()),
                locked != null && !c.date().isAfter(locked));
    }

    @Override
    public CollectionEntry save(CollectionEntry input) {
        CollectionEntry c = CollectionRules.normalize(input);
        List<FieldError> errors = new ArrayList<>(CollectionRules.validate(c, ledger.today()));
        if (c.kind() != CollectionKind.COLLECTION) {
            errors.add(FieldError.of(null, "collection.creditNoteThroughAction"));
        }
        ValidationException.throwIfAny(errors);
        return c.isNew() ? create(c) : update(c);
    }

    private CollectionEntry create(CollectionEntry c) {
        return tx.inTransaction(conn -> {
            List<FieldError> errors = new ArrayList<>();
            Driver driver = checkParties(c, errors);
            ValidationException.throwIfAny(errors);
            CollectionEntry draft = new CollectionEntry(null, 0, c.date(), clock.instant(), c.driverId(), c.vehicleId(),
                    c.accountId(), c.amount(), CollectionKind.COLLECTION, CollectionStatus.VALID, null, null,
                    ledger.user(), null, c.notes());
            long id = collections.insert(draft);
            FinancialTransaction posted = ledger.post(new FinancialTransaction(null, 0, c.date(), null,
                    TransactionDirection.INFLOW, categories.system(SystemCategory.COLLECTION).id(), null, c.accountId(),
                    c.amount(), c.vehicleId(), c.driverId(), "Versement — " + driver.displayName(), null,
                    TransactionSource.COLLECTION, id, null, null, null));
            CollectionEntry linked = draft.withIdentity(id, 0).withTransaction(posted.id());
            long version = collections.update(linked);
            CollectionEntry saved = linked.withIdentity(id, version);
            audit.created(AUDIT_TYPE, id, collections.snapshot(saved));
            if (points.isPointed(c.driverId(), c.date())) {
                driverLedger.post(c.driverId(), ledger.today(), LedgerRubric.SHORTAGE, -c.amount().minor(),
                        LedgerSource.REGULARISATION, id, "Versement après point du " + formats.date(c.date()));
            }
            return saved;
        });
    }

    private CollectionEntry update(CollectionEntry c) {
        return tx.inTransaction(conn -> {
            CollectionEntry before = collections.findById(c.id())
                    .orElseThrow(() -> new StaleDataException(AUDIT_TYPE, c.id(), c.version()));
            if (before.version() != c.version()) {
                throw new StaleDataException(AUDIT_TYPE, c.id(), c.version());
            }
            List<FieldError> errors = new ArrayList<>();
            String lock = lockReason(before);
            if (lock != null) {
                throw ValidationException.of(FieldError.of(null, lock));
            }
            errors.addAll(CollectionRules.validateChange(before, c));
            Driver driver = checkParties(c, errors);
            ValidationException.throwIfAny(errors);
            CollectionEntry after = new CollectionEntry(before.id(), before.version(), before.date(), before.recordedAt(),
                    c.driverId(), c.vehicleId(), c.accountId(), c.amount(), before.kind(), before.status(), null,
                    before.transactionId(), before.cashier(), c.reason(), c.notes());
            FinancialTransaction txBefore = transactions.findById(before.transactionId()).orElseThrow();
            FinancialTransaction txAfter = new FinancialTransaction(txBefore.id(), txBefore.version(), txBefore.date(),
                    txBefore.recordedAt(), txBefore.direction(), txBefore.categoryId(), txBefore.nature(),
                    c.accountId(), c.amount(), c.vehicleId(), c.driverId(), "Versement — " + driver.displayName(),
                    txBefore.reference(), txBefore.source(), txBefore.sourceId(), null, null, txBefore.userName());
            ledger.amendCollection(txBefore, txAfter, c.reason());
            long version = collections.update(after);
            audit.updated(AUDIT_TYPE, before.id(), collections.snapshot(before), collections.snapshot(after), c.reason());
            return after.withIdentity(after.id(), version);
        });
    }

    /** Avoir : annule le versement en totalité, daté du jour. */
    public CollectionEntry creditNote(long collectionId, String reason) {
        return tx.inTransaction(conn -> {
            CollectionEntry origin = collections.findById(collectionId)
                    .orElseThrow(() -> ValidationException.of(FieldError.of(null, "collection.unknown")));
            if (origin.isCreditNote()) {
                throw ValidationException.of(FieldError.of(null, "collection.locked.creditNote"));
            }
            if (origin.status() != CollectionStatus.VALID) {
                throw ValidationException.of(FieldError.of(null, "collection.locked.credited"));
            }
            if (reason == null || reason.isBlank()) {
                throw ValidationException.of(FieldError.of("reason", "collection.reasonRequired"));
            }
            LocalDate today = ledger.today();
            CollectionEntry draft = CollectionRules.creditNoteFor(origin, today, clock.instant(), ledger.user(), reason);
            long id = collections.insert(draft);
            FinancialTransaction reversal = ledger.reverse(origin.transactionId(), reason, today);
            CollectionEntry linked = draft.withIdentity(id, 0).withTransaction(reversal.id());
            long version = collections.update(linked);
            collections.update(origin.credited());
            audit.created(AUDIT_TYPE, id, collections.snapshot(linked));
            audit.event(AUDIT_TYPE, origin.id(), AuditAction.REVERSE, Map.of("credit_note_id", id), reason);
            if (points.isPointed(origin.driverId(), today)) {
                driverLedger.post(origin.driverId(), today, LedgerRubric.SHORTAGE, origin.amount().minor(),
                        LedgerSource.REGULARISATION, id, "Avoir après point du " + formats.date(today));
            }
            return linked.withIdentity(id, version);
        });
    }

    private Driver checkParties(CollectionEntry c, List<FieldError> errors) {
        Driver driver = drivers.findById(c.driverId()).orElse(null);
        if (driver == null) {
            errors.add(FieldError.of("driverId", "collection.driver.unknown"));
        }
        if (vehicles.findById(c.vehicleId()).isEmpty()) {
            errors.add(FieldError.of("vehicleId", "collection.vehicle.unknown"));
        }
        return driver;
    }
}
