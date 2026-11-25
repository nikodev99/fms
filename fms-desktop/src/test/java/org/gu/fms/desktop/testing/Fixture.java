package org.gu.fms.desktop.testing;

import org.gu.fms.desktop.features.allocation.AllocationRuleRepository;
import org.gu.fms.desktop.features.allocation.AllocationRuleService;
import org.gu.fms.desktop.features.collection.*;
import org.gu.fms.desktop.features.document.*;
import org.gu.fms.desktop.features.driver.AssignmentRepository;
import org.gu.fms.desktop.features.driver.AssignmentService;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.driver.DriverService;
import org.gu.fms.desktop.features.finance.*;
import org.gu.fms.desktop.features.loan.LoanInstalmentRepository;
import org.gu.fms.desktop.features.loan.LoanRepository;
import org.gu.fms.desktop.features.loan.LoanReversalHandler;
import org.gu.fms.desktop.features.loan.LoanService;
import org.gu.fms.desktop.features.maintenance.*;
import org.gu.fms.desktop.features.payroll.*;
import org.gu.fms.desktop.features.security.AuthService;
import org.gu.fms.desktop.features.security.UserRepository;
import org.gu.fms.desktop.features.security.UserService;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.features.vehicle.VehicleCategoryRepository;
import org.gu.fms.desktop.features.vehicle.VehicleCategoryService;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.features.vehicle.VehicleService;
import org.gu.fms.desktop.platform.audit.AuditLog;
import org.gu.fms.desktop.platform.file.FileStore;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.security.PasswordHasher;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.desktop.platform.settings.SettingsStore;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.PayScheme;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;

/** Services câblés à la main sur une vraie base SQLite migrée (sans Guice), pour les tests d'intégration. */
public final class Fixture implements AutoCloseable {

    public static final Clock CLOCK = TestDatabase.CLOCK;
    public static final AppSettings SETTINGS = AppSettings.CONGO;

    public final TestDatabase db;
    public final AuditLog audit;
    public final SettingsStore settingsStore;
    public final Formats formats;
    public final VehicleCategoryRepository categoryRepo;
    public final VehicleRepository vehicleRepo;
    public final DriverRepository driverRepo;
    public final AssignmentRepository assignmentRepo;
    public final MaintenanceTypeRepository typeRepo;
    public final MaintenanceEventRepository eventRepo;
    public final MaintenancePlanRepository planRepo;
    public final VehicleCategoryService categories;
    public final VehicleService vehicles;
    public final DriverService drivers;
    public final AssignmentService assignments;
    public final MaintenanceEventService events;
    public final MaintenancePlanService plans;
    public final FleetPreferencesService fleetPrefs;
    public final RepairThresholdService repairs;
    public final PayrollContributionService contributions;
    public final ItsBandService itsBands;
    public final ItsSettingsService itsSettings;
    public final AllocationRuleService allocations;
    // Trésorerie, versements, paie (horloge réglable)
    public final MutableClock clock = new MutableClock(CLOCK.instant());
    public final UserSession session = new UserSession();
    public final AccountRepository accountRepo;
    public final CategoryRepository txCategoryRepo;
    public final TransactionRepository transactionRepo;
    public final ClosingRepository closingRepo;
    public final TransferRepository transferRepo;
    public final Ledger ledger;
    public final AccountService accounts;
    public final CategoryService txCategories;
    public final TransactionService transactions;
    public final TransferService transfers;
    public final CollectionRepository collectionRepo;
    public final DailyPointRepository pointRepo;
    public final DriverLedgerRepository driverLedgerRepo;
    public final DriverLedgerService driverLedger;
    public final CollectionService collections;
    public final CollectionPolicyService collectionPolicy;
    public final DailyPointService dailyPoints;
    public final ClosingService closings;
    public final PayslipRepository payslipRepo;
    public final RecoveryPolicyService recoveryPolicy;
    public final PayrollService payroll;
    public final AdvanceService advances;
    public final MaintenancePaymentService maintenancePayments;
    public final ReversalService reversals;
    public final LoanRepository loanRepo;
    public final LoanInstalmentRepository instalmentRepo;
    public final LoanService loans;
    public final DocumentTypeRepository documentTypeRepo;
    public final FleetDocumentRepository documentRepo;
    public final FileStore fileStore;
    public final DocumentFileRepository documentFileRepo;
    public final DocumentService documents;
    public final ComplianceService compliance;
    public final CollectionCalendarService calendar;
    /** Session des tests de connexion (indépendante de la session « propriétaire » des autres services). */
    public final UserSession authSession = new UserSession();
    public final UserRepository userRepo;
    public final PasswordHasher hasher;
    public final AuthService auth;
    public final UserService users;

    public Fixture(Path dir) throws Exception {
        db = TestDatabase.migrated(dir);
        audit = new AuditLog(db.jdbc, CLOCK, new UserSession());
        settingsStore = new SettingsStore(db.jdbc, db.tx, audit, CLOCK);
        formats = new Formats(SETTINGS);
        categoryRepo = new VehicleCategoryRepository(db.jdbc, CLOCK);
        vehicleRepo = new VehicleRepository(db.jdbc, CLOCK, SETTINGS);
        driverRepo = new DriverRepository(db.jdbc, CLOCK, SETTINGS);
        assignmentRepo = new AssignmentRepository(db.jdbc, CLOCK, SETTINGS);
        typeRepo = new MaintenanceTypeRepository(db.jdbc, CLOCK);
        eventRepo = new MaintenanceEventRepository(db.jdbc, CLOCK, SETTINGS);
        planRepo = new MaintenancePlanRepository(db.jdbc, CLOCK);
        categories = new VehicleCategoryService(categoryRepo, db.tx, audit, CLOCK, SETTINGS);
        vehicles = new VehicleService(vehicleRepo, categoryRepo, db.tx, audit, CLOCK, SETTINGS);
        drivers = new DriverService(driverRepo, assignmentRepo, db.tx, audit, CLOCK, SETTINGS);
        assignments = new AssignmentService(assignmentRepo, driverRepo, vehicleRepo, formats, db.tx, audit, CLOCK, SETTINGS);
        transactionRepo = new TransactionRepository(db.jdbc, clock, SETTINGS);
        events = new MaintenanceEventService(eventRepo, vehicleRepo, typeRepo, transactionRepo, db.tx, audit, CLOCK,
                SETTINGS);
        plans = new MaintenancePlanService(planRepo, vehicleRepo, typeRepo, db.tx, audit, CLOCK, SETTINGS);
        fleetPrefs = new FleetPreferencesService(settingsStore, SETTINGS);
        repairs = new RepairThresholdService(vehicleRepo, eventRepo, fleetPrefs, SETTINGS);
        contributions = new PayrollContributionService(new PayrollContributionRepository(db.jdbc, CLOCK, SETTINGS),
                db.tx, audit, CLOCK, SETTINGS);
        itsBands = new ItsBandService(new ItsBandRepository(db.jdbc, CLOCK, SETTINGS), db.tx, audit, CLOCK, SETTINGS);
        itsSettings = new ItsSettingsService(settingsStore, SETTINGS);
        allocations = new AllocationRuleService(new AllocationRuleRepository(db.jdbc, CLOCK, SETTINGS), db.tx, audit,
                CLOCK, SETTINGS);

        // Les comptes livrés s'ouvrent à la date de migration : on les ouvre au 1er janvier pour les scénarios.
        db.jdbc.update("UPDATE financial_account SET opening_date = '2026-01-01'");
        AuditLog clocked = new AuditLog(db.jdbc, clock, session);
        accountRepo = new AccountRepository(db.jdbc, clock, SETTINGS);
        txCategoryRepo = new CategoryRepository(db.jdbc, clock);
        closingRepo = new ClosingRepository(db.jdbc, clock, SETTINGS);
        transferRepo = new TransferRepository(db.jdbc, clock, SETTINGS);
        ledger = new Ledger(transactionRepo, accountRepo, txCategoryRepo, closingRepo, db.tx, clocked, session, clock,
                SETTINGS, formats);
        accounts = new AccountService(accountRepo, ledger, db.tx, clocked, clock, SETTINGS);
        txCategories = new CategoryService(txCategoryRepo, db.tx, clocked, clock, SETTINGS);
        transactions = new TransactionService(transactionRepo, txCategoryRepo, ledger);
        transfers = new TransferService(transferRepo, accountRepo, txCategoryRepo, ledger, db.tx, clocked);
        collectionRepo = new CollectionRepository(db.jdbc, clock, SETTINGS);
        collectionPolicy = new CollectionPolicyService(settingsStore);
        pointRepo = new DailyPointRepository(db.jdbc, clock, SETTINGS);
        driverLedgerRepo = new DriverLedgerRepository(db.jdbc, clock, SETTINGS);
        driverLedger = new DriverLedgerService(driverLedgerRepo, db.tx, clocked, session, clock, SETTINGS);
        collections = new CollectionService(collectionRepo, pointRepo, driverRepo, vehicleRepo, txCategoryRepo,
                transactionRepo, ledger, driverLedger, db.tx, clocked, clock, formats);
        dailyPoints = new DailyPointService(pointRepo, collectionRepo, assignmentRepo, driverRepo, vehicleRepo,
                driverLedger, ledger, db.tx, clocked, clock, SETTINGS, formats, collectionPolicy);
        closings = new ClosingService(closingRepo, accountRepo, transactionRepo, txCategoryRepo, ledger, dailyPoints,
                db.tx, clocked, clock, formats, session);
        payslipRepo = new PayslipRepository(db.jdbc, clock, SETTINGS);
        recoveryPolicy = new RecoveryPolicyService(settingsStore);
        payroll = new PayrollService(payslipRepo, driverRepo, collectionRepo,
                new PayrollContributionRepository(db.jdbc, CLOCK, SETTINGS), new ItsBandRepository(db.jdbc, CLOCK, SETTINGS),
                itsSettings, recoveryPolicy, driverLedger, txCategoryRepo, ledger, db.tx, clocked, clock, SETTINGS, session);
        advances = new AdvanceService(ledger, transactionRepo, txCategoryRepo, driverRepo, driverLedger,
                driverLedgerRepo, db.tx, formats);
        maintenancePayments = new MaintenancePaymentService(eventRepo, transactionRepo, txCategoryRepo, ledger, db.tx,
                formats, SETTINGS);
        loanRepo = new LoanRepository(db.jdbc, clock, SETTINGS);
        instalmentRepo = new LoanInstalmentRepository(db.jdbc, clock, SETTINGS);
        loans = new LoanService(loanRepo, instalmentRepo, vehicleRepo, accountRepo, txCategoryRepo, ledger, db.tx,
                clocked, formats, SETTINGS, session);
        reversals = new ReversalService(transactionRepo, ledger, db.tx, Set.of(
                new TransferReversalHandler(transfers, transactionRepo), new PayrollReversalHandler(payroll), advances,
                new LoanReversalHandler(loans)), session);
        documentTypeRepo = new DocumentTypeRepository(db.jdbc, clock);
        documentRepo = new FleetDocumentRepository(db.jdbc, clock);
        fileStore = new FileStore(db.jdbc, clock, session);
        documentFileRepo = new DocumentFileRepository(db.jdbc, clock);
        documents = new DocumentService(documentRepo, documentTypeRepo, vehicleRepo, driverRepo, documentFileRepo,
                fileStore, db.tx, clocked, clock, SETTINGS);
        compliance = new ComplianceService(documentRepo, documentTypeRepo, vehicleRepo, driverRepo, assignmentRepo,
                documentFileRepo, fleetPrefs, clock, SETTINGS);
        calendar = new CollectionCalendarService(collectionRepo, pointRepo, assignmentRepo, driverRepo,
                collectionPolicy, clock, SETTINGS);
        userRepo = new UserRepository(db.jdbc, clock);
        hasher = new PasswordHasher(100_000);
        auth = new AuthService(userRepo, hasher, authSession, settingsStore, db.tx, clocked, clock);
        users = new UserService(userRepo, hasher, authSession, db.tx, clocked);
        session.signIn(UserSession.User.owner(1L, "proprietaire", "Propriétaire"));
    }

    public static Money xaf(long amount) {
        return Money.of(amount, CurrencyUnit.XAF);
    }

    public Vehicle vehicle(String registration) {
        return vehicles.save(new Vehicle(null, 0, registration, "Toyota", "Corolla", 1L, Vehicle.VehicleStatus.ACTIVE, null,
                null, xaf(8_000_000), 10_000L, xaf(1_000_000), null));
    }

    public Driver driver(String lastName, String idNumber) {
        return drivers.save(new Driver(null, 0, lastName, "Jean", "06 474 50 67", null, idNumber, null,
                Driver.DriverStatus.ACTIVE, PayScheme.PERCENTAGE, null, Percent.of("15"),
                xaf(25_000), null, false, null, true, null, null, null));
    }

    /** Compte livré par la migration (Caisse, MTN Mobile Money, Airtel Money). */
    public long account(String name) {
        return db.jdbc.queryLong("SELECT id FROM financial_account WHERE name = ?", name);
    }

    public long auditCount(String entityType) {
        return db.jdbc.queryLong("SELECT COUNT(*) FROM audit_log WHERE entity_type = ?", entityType);
    }

    @Override
    public void close() throws Exception {
        db.close();
    }
}