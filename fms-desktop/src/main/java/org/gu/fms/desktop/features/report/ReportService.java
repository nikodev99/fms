package org.gu.fms.desktop.features.report;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.collection.CollectionCalendarService;
import org.gu.fms.desktop.features.collection.CollectionRepository;
import org.gu.fms.desktop.features.document.DocumentTypeRepository;
import org.gu.fms.desktop.features.document.FleetDocumentRepository;
import org.gu.fms.desktop.features.driver.AssignmentRepository;
import org.gu.fms.desktop.features.driver.DriverRepository;
import org.gu.fms.desktop.features.finance.*;
import org.gu.fms.desktop.features.loan.LoanRepository;
import org.gu.fms.desktop.features.maintenance.MaintenanceEventRepository;
import org.gu.fms.desktop.features.maintenance.MaintenancePaymentService;
import org.gu.fms.desktop.features.maintenance.MaintenanceTypeRepository;
import org.gu.fms.desktop.features.settings.CompanyService;
import org.gu.fms.desktop.features.settings.FleetPreferencesService;
import org.gu.fms.desktop.features.vehicle.VehicleCategoryRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.db.TransactionManager;
import org.gu.fms.desktop.platform.file.FileStore;
import org.gu.fms.desktop.platform.file.StoredFile;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.report.PdfReport;
import org.gu.fms.desktop.platform.report.PdfReport.Align;
import org.gu.fms.desktop.platform.session.UserSession;
import org.gu.fms.desktop.platform.settings.AppSettings;
import org.gu.fms.domain.collection.CalendarState;
import org.gu.fms.domain.collection.CollectionCalendar;
import org.gu.fms.domain.collection.CollectionPolicy;
import org.gu.fms.domain.company.CompanyProfile;
import org.gu.fms.domain.document.DocumentRules;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.document.FleetDocument;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.DriverAssignment;
import org.gu.fms.domain.finance.*;
import org.gu.fms.domain.loan.Loan;
import org.gu.fms.domain.maintenance.MaintenanceEvent;
import org.gu.fms.domain.maintenance.MaintenanceType;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.VehicleCategory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Les quatre rapports PDF du poste : relevé de compte, fiche véhicule (toutes les interventions et pannes), clôtures
 * de caisse du mois, rapport mensuel des versements.
 *
 * Chaque rapport généré est enregistré dans la base avec sa référence (historique consultable, sauvegardé avec les
 * données) ; il se prévisualise et se télécharge depuis l'application. Le nom du fichier porte la référence.
 */
@Singleton
public final class ReportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final CompanyService company;
    private final FileStore files;
    private final GeneratedReportRepository history;
    private final TransactionManager tx;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final Ledger ledger;
    private final ClosingRepository closings;
    private final VehicleRepository vehicles;
    private final VehicleCategoryRepository vehicleCategories;
    private final DriverRepository drivers;
    private final AssignmentRepository assignments;
    private final MaintenanceEventRepository events;
    private final MaintenanceTypeRepository maintenanceTypes;
    private final MaintenancePaymentService maintenancePayments;
    private final FleetDocumentRepository documents;
    private final DocumentTypeRepository documentTypes;
    private final LoanRepository loans;
    private final CollectionRepository collections;
    private final CollectionCalendarService calendar;
    private final FleetPreferencesService prefs;
    private final UserSession session;
    private final Messages messages;
    private final Formats formats;
    private final Clock clock;
    private final AppSettings settings;

    @Inject
    public ReportService(CompanyService company, FileStore files, GeneratedReportRepository history,
                         TransactionManager tx, AccountRepository accounts,
                         TransactionRepository transactions, CategoryRepository categories, Ledger ledger,
                         ClosingRepository closings, VehicleRepository vehicles,
                         VehicleCategoryRepository vehicleCategories, DriverRepository drivers,
                         AssignmentRepository assignments, MaintenanceEventRepository events,
                         MaintenanceTypeRepository maintenanceTypes, MaintenancePaymentService maintenancePayments,
                         FleetDocumentRepository documents, DocumentTypeRepository documentTypes, LoanRepository loans,
                         CollectionRepository collections, CollectionCalendarService calendar,
                         FleetPreferencesService prefs, UserSession session, Messages messages, Formats formats,
                         Clock clock, AppSettings settings) {
        this.company = company;
        this.files = files;
        this.history = history;
        this.tx = tx;
        this.accounts = accounts;
        this.transactions = transactions;
        this.categories = categories;
        this.ledger = ledger;
        this.closings = closings;
        this.vehicles = vehicles;
        this.vehicleCategories = vehicleCategories;
        this.drivers = drivers;
        this.assignments = assignments;
        this.events = events;
        this.maintenanceTypes = maintenanceTypes;
        this.maintenancePayments = maintenancePayments;
        this.documents = documents;
        this.documentTypes = documentTypes;
        this.loans = loans;
        this.collections = collections;
        this.calendar = calendar;
        this.prefs = prefs;
        this.session = session;
        this.messages = messages;
        this.formats = formats;
        this.clock = clock;
        this.settings = settings;
    }

    /** Rapports générés, du plus récent au plus ancien. */
    public List<GeneratedReport> history() {
        return history.findAll();
    }

    /** Contenu PDF d'un rapport généré (aperçu, téléchargement). */
    public byte[] content(GeneratedReport report) {
        return files.content(report.file().id());
    }

    // ---- Relevé de compte ------------------------------------------------------------------------------------------

    public GeneratedReport accountStatement(long accountId, LocalDate from, LocalDate to) {
        FinancialAccount account = accounts.findById(accountId).orElseThrow();
        Map<Long, TransactionCategory> cats = categoryMap();
        List<FinancialTransaction> list = transactions.findBetween(from, to).stream()
                .filter(t -> t.accountId().equals(accountId))
                .sorted(Comparator.comparing(FinancialTransaction::date).thenComparing(FinancialTransaction::id))
                .toList();
        Money opening = ledger.balanceAt(accountId, from.minusDays(1));
        long in = 0;
        long out = 0;
        for (FinancialTransaction t : list) {
            if (t.signedMinor() > 0) {
                in += t.signedMinor();
            } else {
                out -= t.signedMinor();
            }
        }
        long closingBalance = opening.minor() + in - out;
        String ref = reference(ReportKind.ACCOUNT_STATEMENT);
        String subject = messages.get("report.statement.subtitle", account.name(), formats.date(from), formats.date(to));
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        try (PdfReport pdf = PdfReport.create(pdfOut, false, header(messages.get("report.statement.title"), subject, ref))) {
            pdf.figures(List.of(
                    new String[] {messages.get("report.statement.opening"), money(opening.minor()), formats.date(from)},
                    new String[] {messages.get("report.statement.in"), money(in), null},
                    new String[] {messages.get("report.statement.out"), money(out), null},
                    new String[] {messages.get("report.statement.closing"), money(closingBalance), formats.date(to)}));
            PdfReport.Table t = pdf.table(new float[] {1.6f, 3, 4.2f, 1.8f, 2, 2, 2.2f},
                    new String[] {"Date", messages.get("transaction.category"), messages.get("transaction.label"),
                            messages.get("report.ref"), messages.get("report.in"), messages.get("report.out"),
                            messages.get("report.balance")},
                    new Align[] {Align.LEFT, Align.LEFT, Align.LEFT, Align.LEFT, Align.RIGHT, Align.RIGHT, Align.RIGHT});
            long running = opening.minor();
            t.row(formats.date(from), messages.get("report.statement.opening"), "", "", "", "", money(running));
            for (FinancialTransaction tx : list) {
                running += tx.signedMinor();
                TransactionCategory c = cats.get(tx.categoryId());
                String label = (tx.label() == null ? "" : tx.label()) + (tx.isReversal()
                        ? " (" + messages.get("report.reversal") + ")" : "");
                t.row(formats.date(tx.date()), c == null ? "" : c.name(), label,
                        tx.reference() == null ? "" : tx.reference(),
                        tx.signedMinor() > 0 ? amount(tx.signedMinor()) : "",
                        tx.signedMinor() < 0 ? amount(-tx.signedMinor()) : "", amount(running));
            }
            t.total("", messages.get("report.total"), "", "", amount(in), amount(out), amount(closingBalance));
            t.end();
            pdf.note(messages.get("report.statement.note", list.size()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return store(ReportKind.ACCOUNT_STATEMENT, ref, subject, slug(account.name()), pdfOut);
    }

    // ---- Fiche véhicule --------------------------------------------------------------------------------------------

    public GeneratedReport vehicleSheet(long vehicleId) {
        Vehicle v = vehicles.findById(vehicleId).orElseThrow();
        LocalDate today = today();
        Map<Long, MaintenanceType> types = maintenanceTypes.findAll().stream()
                .collect(Collectors.toMap(MaintenanceType::id, Function.identity()));
        Map<Long, Long> paid = maintenancePayments.paidByEvent();
        Map<Long, Driver> driverById = drivers.findAll().stream()
                .collect(Collectors.toMap(Driver::id, Function.identity()));
        Map<Long, DocumentType> docTypes = documentTypes.findAll().stream()
                .collect(Collectors.toMap(DocumentType::id, Function.identity()));
        VehicleCategory category = vehicleCategories.findById(v.categoryId()).orElse(null);
        String ref = reference(ReportKind.VEHICLE_SHEET);
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        try (PdfReport pdf = PdfReport.create(pdfOut, false, header(messages.get("report.vehicle.title"),
                v.displayName(), ref))) {
            List<String[]> d = new ArrayList<>();
            d.add(new String[] {messages.get("vehicle.registration"), v.registration()});
            d.add(new String[] {messages.get("vehicle.category"), category == null ? "" : category.name()});
            d.add(new String[] {messages.get("vehicles.column.vehicle"), v.make() + " " + v.model()});
            d.add(new String[] {messages.get("vehicle.status"), messages.label(v.status())});
            d.add(new String[] {messages.get("vehicle.acquisitionDate"), date(v.acquisitionDate())});
            d.add(new String[] {messages.get("vehicle.acquisitionMethod"),
                    v.acquisitionMethod() == null ? "—" : messages.label(v.acquisitionMethod())});
            d.add(new String[] {messages.get("vehicle.purchasePrice"), money(v.purchasePrice())});
            d.add(new String[] {messages.get("vehicle.currentMileage"),
                    v.currentMileage() == null ? "—" : formats.number(v.currentMileage()) + " km"});
            d.add(new String[] {messages.get("vehicle.repairThreshold"), money(v.repairThreshold())});
            List<Loan> financing = loans.findAll().stream().filter(l -> l.vehicleIds().contains(v.id())).toList();
            if (!financing.isEmpty()) {
                d.add(new String[] {messages.get("report.vehicle.loans"),
                        financing.stream().map(Loan::label).collect(Collectors.joining(", "))});
            }
            pdf.details(d);

            // Interventions, pannes comprises
            List<MaintenanceEvent> list = events.findAll().stream().filter(e -> e.vehicleId().equals(v.id()))
                    .sorted(Comparator.comparing(MaintenanceEvent::date).thenComparing(MaintenanceEvent::id)).toList();
            long repairs = 0;
            long services = 0;
            long paidTotal = 0;
            int breakdowns = 0;
            for (MaintenanceEvent e : list) {
                if (e.status() == MaintenanceEvent.MaintenanceEventStatus.CANCELLED) {
                    continue;
                }
                if (e.kind() == MaintenanceEvent.MaintenanceKind.REPAIR) {
                    repairs += e.cost().minor();
                    breakdowns++;
                } else {
                    services += e.cost().minor();
                }
                paidTotal += paid.getOrDefault(e.id(), 0L);
            }
            String share = v.repairThreshold() == null || v.repairThreshold().minor() == 0 ? null
                    : messages.get("report.vehicle.thresholdShare", percent(repairs, v.repairThreshold().minor()));
            long collected = collections.findAll().stream().filter(c -> c.vehicleId().equals(v.id()))
                    .mapToLong(c -> c.isCreditNote() ? -c.amount().minor() : c.amount().minor()).sum();
            pdf.figures(List.of(
                    new String[] {messages.get("report.vehicle.repairs"), money(repairs),
                            messages.get("report.vehicle.breakdowns", breakdowns) + (share == null ? "" : " — " + share)},
                    new String[] {messages.get("report.vehicle.services"), money(services), null},
                    new String[] {messages.get("report.vehicle.unpaid"), money(repairs + services - paidTotal), null},
                    new String[] {messages.get("report.vehicle.collected"), money(collected),
                            messages.get("report.vehicle.collected.caption")}));

            pdf.section(messages.get("report.vehicle.interventions", list.size()));
            PdfReport.Table t = pdf.table(new float[] {1.6f, 2.6f, 1.5f, 4.2f, 1.6f, 2, 2},
                    new String[] {"Date", messages.get("report.vehicle.type"), messages.get("report.vehicle.kind"),
                            messages.get("report.vehicle.descriptionColumn"), "Km", messages.get("report.vehicle.cost"),
                            messages.get("report.vehicle.paid")},
                    new Align[] {Align.LEFT, Align.LEFT, Align.LEFT, Align.LEFT, Align.RIGHT, Align.RIGHT, Align.RIGHT});
            for (MaintenanceEvent e : list) {
                MaintenanceType type = types.get(e.typeId());
                boolean cancelled = e.status() == MaintenanceEvent.MaintenanceEventStatus.CANCELLED;
                String description = String.join(" — ", java.util.stream.Stream.of(e.description(), e.supplier())
                        .filter(s -> s != null && !s.isBlank()).toList())
                        + (cancelled ? " [" + messages.get("report.cancelled") + " : " + e.cancelReason() + "]" : "");
                t.row(formats.date(e.date()), type == null ? "" : type.name(), messages.label(e.kind()), description,
                        e.mileage() == null ? "" : formats.number(e.mileage()),
                        cancelled ? "(" + amount(e.cost().minor()) + ")" : amount(e.cost().minor()),
                        amount(paid.getOrDefault(e.id(), 0L)));
            }
            if (!list.isEmpty()) {
                t.total("", messages.get("report.total"), "", "", "", amount(repairs + services), amount(paidTotal));
            }
            t.end();
            if (list.isEmpty()) {
                pdf.note(messages.get("report.vehicle.noIntervention"));
            }

            // Documents : ceux du véhicule (historique compris) et ceux des chauffeurs qui le conduisent aujourd'hui
            List<DriverAssignment> current = assignments.findActiveOn(today).stream()
                    .filter(a -> a.vehicleId().equals(v.id())).toList();
            List<String[]> docRows = new ArrayList<>();
            long alertDays = prefs.load().documentAlertDays();
            String vehicleHolder = messages.get("vehicle.documents.vehicle");
            for (FleetDocument doc : documents.forOwner(DocumentType.DocumentSubject.VEHICLE, v.id())) {
                docRows.add(documentRow(doc, vehicleHolder, docTypes, today, alertDays));
            }
            java.util.Set<Long> seenDrivers = new java.util.HashSet<>();
            for (DriverAssignment a : current) {
                Driver drv = driverById.get(a.driverId());
                if (drv == null || !seenDrivers.add(drv.id())) {
                    continue;
                }
                for (FleetDocument doc : documents.forOwner(DocumentType.DocumentSubject.DRIVER, drv.id())) {
                    if (doc.isCurrent()) {
                        docRows.add(documentRow(doc, drv.displayName(), docTypes, today, alertDays));
                    }
                }
            }
            pdf.section(messages.get("report.vehicle.documents"));
            PdfReport.Table dt = pdf.table(new float[] {3, 2.6f, 2.4f, 1.8f, 2, 1.8f},
                    new String[] {messages.get("document.type"), messages.get("document.subject"),
                            messages.get("document.number"), messages.get("document.issuedOn"),
                            messages.get("document.expiresOn"), messages.get("document.status")},
                    new Align[] {Align.LEFT, Align.LEFT, Align.LEFT, Align.LEFT, Align.LEFT, Align.LEFT});
            docRows.forEach(dt::row);
            dt.end();
            if (docRows.isEmpty()) {
                pdf.note(messages.get("report.vehicle.noDocument"));
            }

            // Affectations
            List<DriverAssignment> assigned = assignments.findAll().stream().filter(a -> a.vehicleId().equals(v.id()))
                    .sorted(Comparator.comparing(DriverAssignment::startDate)).toList();
            pdf.section(messages.get("report.vehicle.assignments"));
            PdfReport.Table at = pdf.table(new float[] {4, 2, 2, 2, 2.4f},
                    new String[] {messages.get("calendar.driver"), messages.get("report.vehicle.role"),
                            messages.get("report.from"), messages.get("report.to"), messages.get("calendar.expected")},
                    new Align[] {Align.LEFT, Align.LEFT, Align.LEFT, Align.LEFT, Align.RIGHT});
            for (DriverAssignment a : assigned) {
                Driver drv = driverById.get(a.driverId());
                at.row(drv == null ? "" : drv.displayName(), messages.label(a.role()), formats.date(a.startDate()),
                        a.endDate() == null ? messages.get("report.ongoing") : formats.date(a.endDate()),
                        a.expectedDaily() == null ? "" : amount(a.expectedDaily().minor()));
            }
            at.end();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return store(ReportKind.VEHICLE_SHEET, ref, v.displayName(), slug(v.registration()), pdfOut);
    }

    private String[] documentRow(FleetDocument doc, String holder, Map<Long, DocumentType> docTypes, LocalDate today,
                                 long alertDays) {
        DocumentType type = docTypes.get(doc.typeId());
        String state = doc.isCurrent()
                ? messages.label(DocumentRules.status(doc, true, today, alertDays))
                : messages.get("report.vehicle.replaced");
        return new String[] {type == null ? "" : type.name(), holder, doc.number() == null ? "" : doc.number(),
                date(doc.issuedOn()), doc.expiresOn() == null ? messages.get("document.noExpiry")
                : formats.date(doc.expiresOn()), state};
    }

    // ---- Clôtures du mois ------------------------------------------------------------------------------------------

    public GeneratedReport monthlyClosings(YearMonth month) {
        Map<Long, FinancialAccount> accountById = accounts.findAll().stream()
                .collect(Collectors.toMap(FinancialAccount::id, Function.identity()));
        List<CashClosing> list = closings.findBetween(month.atDay(1), month.atEndOfMonth()).stream()
                .sorted(Comparator.comparing(CashClosing::date).thenComparing(CashClosing::accountId)).toList();
        String ref = reference(ReportKind.MONTHLY_CLOSINGS);
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        try (PdfReport pdf = PdfReport.create(pdfOut, true, header(messages.get("report.closings.title"),
                monthName(month), ref))) {
            long shortages = 0;
            long surpluses = 0;
            int reopened = 0;
            for (CashClosing c : list) {
                long diff = c.difference() == null ? 0 : c.difference().minor();
                if (c.status() == ClosingStatus.REOPENED) {
                    reopened++;
                    continue;
                }
                if (diff < 0) {
                    shortages -= diff;
                } else {
                    surpluses += diff;
                }
            }
            pdf.figures(List.of(
                    new String[] {messages.get("report.closings.count"), Integer.toString(list.size() - reopened),
                            reopened == 0 ? null : messages.get("report.closings.reopened", reopened)},
                    new String[] {messages.get("report.closings.shortages"), money(shortages), null},
                    new String[] {messages.get("report.closings.surpluses"), money(surpluses), null}));
            PdfReport.Table t = pdf.table(new float[] {1.5f, 2.6f, 2, 2, 2, 2, 2, 2, 1.8f, 2.4f, 1.6f},
                    new String[] {"Date", messages.get("transaction.account"), messages.get("report.closings.opening"),
                            messages.get("report.in"), messages.get("report.out"),
                            messages.get("report.closings.transfers"), messages.get("report.closings.expected"),
                            messages.get("report.closings.actual"), messages.get("report.closings.difference"),
                            messages.get("report.closings.cashier"), messages.get("common.state")},
                    new Align[] {Align.LEFT, Align.LEFT, Align.RIGHT, Align.RIGHT, Align.RIGHT, Align.RIGHT,
                            Align.RIGHT, Align.RIGHT, Align.RIGHT, Align.LEFT, Align.LEFT});
            for (CashClosing c : list) {
                FinancialAccount a = accountById.get(c.accountId());
                long transfers = minor(c.transfersIn()) - minor(c.transfersOut());
                t.row(formats.date(c.date()), a == null ? "" : a.name(), amount(minor(c.openingBalance())),
                        amount(minor(c.inflows())), amount(minor(c.outflows())), amount(transfers),
                        amount(minor(c.expected())), amount(minor(c.actual())), amount(minor(c.difference())),
                        c.cashier() == null ? "" : c.cashier(), messages.label(c.status()));
            }
            t.end();
            if (list.isEmpty()) {
                pdf.note(messages.get("report.closings.none"));
            }
            for (CashClosing c : list) {
                if (c.explanation() != null || c.reopenReason() != null) {
                    FinancialAccount a = accountById.get(c.accountId());
                    pdf.note(formats.date(c.date()) + " — " + (a == null ? "" : a.name()) + " : "
                            + (c.explanation() == null ? "" : c.explanation())
                            + (c.reopenReason() == null ? "" : " [" + messages.get("report.closings.reopenReason") + " : "
                            + c.reopenReason() + "]"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return store(ReportKind.MONTHLY_CLOSINGS, ref, monthName(month), month.toString(), pdfOut);
    }

    // ---- Rapport mensuel des versements ------------------------------------------------------------------------------

    public GeneratedReport monthlyCollections(YearMonth month) {
        CollectionCalendar.Month data = calendar.period(month.atDay(1), month.atEndOfMonth());
        CollectionPolicy policy = calendar.policy();
        CollectionCalendar.Summary s = data.summary();
        String ref = reference(ReportKind.MONTHLY_COLLECTIONS);
        ByteArrayOutputStream pdfOut = new ByteArrayOutputStream();
        Locale locale = messages.locale();
        try (PdfReport pdf = PdfReport.create(pdfOut, true, header(messages.get("report.collections.title"),
                monthName(month) + " — " + messages.get("calendar.unit", formats.currency().code()), ref))) {
            pdf.figures(List.of(
                    new String[] {messages.get("calendar.kpi.expected"), money(s.expected()),
                            messages.get("calendar.kpi.expected.caption", s.workedDays())},
                    new String[] {messages.get("calendar.kpi.received"), money(s.received()),
                            messages.get("calendar.kpi.received.caption", s.entries())},
                    new String[] {messages.get("calendar.kpi.rate"), rate(s.rateBp()),
                            messages.get("calendar.kpi.rate.caption", formats.percent(policy.objective()))},
                    new String[] {messages.get("calendar.kpi.missing"), money(s.missing()),
                            messages.get("calendar.kpi.missing.caption", s.exemptDays())}));

            int days = month.lengthOfMonth();
            float[] widths = new float[days + 4];
            widths[0] = 7;
            for (int i = 1; i <= days; i++) {
                widths[i] = 1.25f;
            }
            widths[days + 1] = 2.4f;
            widths[days + 2] = 2.4f;
            widths[days + 3] = 2.6f;
            String[] headers = new String[days + 4];
            Align[] aligns = new Align[days + 4];
            headers[0] = messages.get("calendar.driver");
            aligns[0] = Align.LEFT;
            for (int d = 1; d <= days; d++) {
                LocalDate date = month.atDay(d);
                headers[d] = d + "\n" + date.getDayOfWeek().getDisplayName(TextStyle.NARROW_STANDALONE, locale)
                        .toUpperCase(locale);
                aligns[d] = Align.CENTER;
            }
            headers[days + 1] = messages.get("calendar.total");
            headers[days + 2] = messages.get("calendar.expected");
            headers[days + 3] = messages.get("calendar.rate");
            aligns[days + 1] = Align.RIGHT;
            aligns[days + 2] = Align.RIGHT;
            aligns[days + 3] = Align.RIGHT;
            PdfReport.Table grid = pdf.table(widths, headers, aligns);
            for (CollectionCalendar.DriverRow r : data.rows()) {
                grid.cell(r.label(), Align.LEFT, true);
                for (CollectionCalendar.Cell c : r.cells()) {
                    Color color = color(c.state());
                    String text = switch (c.state()) {
                        case NONE, REST -> "";
                        case EXEMPT -> "0";
                        default -> thousands(c.received());
                    };
                    grid.colored(text, color, c.state() == CalendarState.PARTIAL || c.state() == CalendarState.REST
                            || c.state() == CalendarState.PENDING || c.state() == CalendarState.NONE);
                }
                grid.cell(thousands(r.received()), Align.RIGHT, false);
                grid.cell(thousands(r.expected()), Align.RIGHT, false);
                grid.cell(rate(r.rateBp()), Align.RIGHT, r.belowObjective());
            }
            grid.end();
            pdf.note(messages.get("report.collections.legend", formats.percent(policy.redThreshold())));

            pdf.section(messages.get("report.collections.byDriver"));
            PdfReport.Table t = pdf.table(new float[] {5, 1.6f, 2.4f, 2.4f, 1.6f, 2.4f, 1.6f, 1.6f},
                    new String[] {messages.get("calendar.driver"), messages.get("report.collections.worked"),
                            messages.get("calendar.expected"), messages.get("calendar.total"),
                            messages.get("calendar.rate"), messages.get("calendar.kpi.missing"),
                            messages.get("report.collections.exempt"), messages.get("report.collections.entries")},
                    new Align[] {Align.LEFT, Align.RIGHT, Align.RIGHT, Align.RIGHT, Align.RIGHT, Align.RIGHT,
                            Align.RIGHT, Align.RIGHT});
            for (CollectionCalendar.DriverRow r : data.rows()) {
                t.row(r.label() + (r.belowObjective() ? " *" : ""), Integer.toString(r.workedDays()),
                        amount(r.expected()), amount(r.received()), rate(r.rateBp()), amount(r.missing()),
                        Integer.toString(r.exemptDays()), Integer.toString(r.entries()));
            }
            t.total(messages.get("report.total"), Integer.toString(s.workedDays()), amount(s.expected()),
                    amount(s.received()), rate(s.rateBp()), amount(s.missing()), Integer.toString(s.exemptDays()),
                    Integer.toString(s.entries()));
            t.end();
            pdf.note(messages.get("report.collections.note", formats.percent(policy.objective()),
                    s.driversBelowObjective()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return store(ReportKind.MONTHLY_COLLECTIONS, ref, monthName(month), month.toString(), pdfOut);
    }

    // ---- Outils ----------------------------------------------------------------------------------------------------

    private static Color color(CalendarState s) {
        return switch (s) {
            case FULL, BONUS -> PdfReport.SUCCESS;
            case PARTIAL -> PdfReport.WARNING;
            case MISSED -> PdfReport.DANGER;
            case EXEMPT -> PdfReport.EXEMPT;
            case REST -> PdfReport.REST;
            case PENDING -> PdfReport.PENDING;
            case NONE -> Color.WHITE;
        };
    }

    private Map<Long, TransactionCategory> categoryMap() {
        return categories.findAll().stream().collect(Collectors.toMap(TransactionCategory::id, Function.identity()));
    }

    /**
     * En-tête : logo et identité de l'entreprise (Paramètres › Entreprise). Sans nom d'entreprise : nom de
     * l'application seul ; sans logo : logo FMS.
     */
    private PdfReport.Header header(String title, String subtitle, String ref) {
        CompanyProfile p = company.load();
        boolean named = p.name() != null && !p.name().isBlank();
        String name = named ? p.name() : messages.get("report.appName");
        String details = !named ? null : String.join(" · ", java.util.stream.Stream.of(p.legalForm(),
                        p.rccm() == null ? null : "RCCM " + p.rccm(), p.niu() == null ? null : "NIU " + p.niu(),
                        p.address(), p.city(), p.phone(), p.email())
                .filter(x -> x != null && !x.isBlank()).toList());
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), settings.zone());
        String generated = messages.get("report.generated", formats.date(now.toLocalDate()),
                now.format(DateTimeFormatter.ofPattern("HH:mm")), session.currentUser().displayName());
        return new PdfReport.Header(name, details, company.logo(p).orElse(null), title, subtitle,
                messages.get("report.reference", ref), generated);
    }

    /** Référence unique : REL-20261123-101500 ; deux rapports de la même seconde reçoivent « -2 », « -3 »… */
    private String reference(ReportKind kind) {
        String base = kind.prefix() + "-" + LocalDateTime.ofInstant(clock.instant(), settings.zone()).format(STAMP);
        String ref = base;
        for (int i = 2; history.exists(ref); i++) {
            ref = base + "-" + i;
        }
        return ref;
    }

    /** Enregistre le PDF dans l'historique. */
    private GeneratedReport store(ReportKind kind, String ref, String subject, String nameSuffix,
                                  ByteArrayOutputStream out) {
        byte[] pdf = out.toByteArray();
        return tx.inTransaction(c -> {
            StoredFile file = files.put(ref + "_" + kind.fileStem() + "-" + nameSuffix + ".pdf", pdf);
            return history.insert(kind, ref, subject, file);
        });
    }

    static String slug(String s) {
        String n = java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return n.replaceAll("(^-|-$)", "");
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), settings.zone());
    }

    private String monthName(YearMonth m) {
        String name = m.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, messages.locale());
        return name.substring(0, 1).toUpperCase(messages.locale()) + name.substring(1) + " " + m.getYear();
    }

    private String money(Money m) {
        return m == null ? "—" : formats.money(m);
    }

    private String money(long minor) {
        return formats.money(Money.of(minor, settings.currency()));
    }

    private String amount(long minor) {
        return formats.amount(Money.of(minor, settings.currency()));
    }

    private static long minor(Money m) {
        return m == null ? 0 : m.minor();
    }

    private String date(LocalDate d) {
        return d == null ? "—" : formats.date(d);
    }

    static String thousands(long minor) {
        return BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString().replace('.', ',');
    }

    static String rate(Long bp) {
        return bp == null ? "—" : BigDecimal.valueOf(bp, 2).setScale(1, RoundingMode.HALF_UP).toPlainString()
                .replace('.', ',') + " %";
    }

    static String percent(long part, long whole) {
        return rate(whole == 0 ? null : Math.round(part * 10_000.0 / whole));
    }
}
