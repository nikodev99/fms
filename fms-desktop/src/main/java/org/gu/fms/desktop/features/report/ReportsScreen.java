package org.gu.fms.desktop.features.report;

import org.gu.fms.desktop.features.finance.AccountRepository;
import org.gu.fms.desktop.features.vehicle.VehicleRepository;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Screen;

import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.filter.MonthPicker;
import org.gu.fms.desktop.platform.ui.form.FilesInput;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.preview.Previews;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.finance.FinancialAccount;
import org.gu.fms.domain.vehicle.Vehicle;

/**
 * Rapports PDF : relevé de compte, fiche véhicule, clôtures du mois, versements du mois. Un rapport généré s'affiche
 * en aperçu, d'où il se télécharge ; il reste ensuite dans la liste des rapports générés (aperçu et téléchargement à
 * la demande, tel qu'il a été produit).
 */
public final class ReportsScreen implements Screen {

    private final CrudContext ui;
    private final Messages messages;
    private final ReportService reports;
    private final AccountRepository accounts;
    private final VehicleRepository vehicles;
    private final Previews previews;
    private DataTable<GeneratedReport> history;
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private final ComboBox<FinancialAccount> account = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<Vehicle> vehicle = new ComboBox<>();
    private MonthPicker closingsMonth;
    private MonthPicker collectionsMonth;
    private Node view;

    @Inject
    public ReportsScreen(CrudContext ui, ReportService reports, AccountRepository accounts, VehicleRepository vehicles,
                         Previews previews) {
        this.ui = ui;
        this.previews = previews;
        this.messages = ui.messages();
        this.reports = reports;
        this.accounts = accounts;
        this.vehicles = vehicles;
    }

    @Override
    public Node view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    @Override
    public void onShow() {
        ui.tasks().job(() -> new Object[] {accounts.findAll(), vehicles.findAll()}).onSuccess(data -> {
            @SuppressWarnings("unchecked")
            List<FinancialAccount> a = (List<FinancialAccount>) data[0];
            @SuppressWarnings("unchecked")
            List<Vehicle> v = (List<Vehicle>) data[1];
            FinancialAccount selectedAccount = account.getValue();
            account.getItems().setAll(a);
            account.setValue(selectedAccount == null && !a.isEmpty() ? a.get(0)
                    : a.stream().filter(x -> selectedAccount != null && x.id().equals(selectedAccount.id())).findFirst()
                    .orElse(a.isEmpty() ? null : a.get(0)));
            Vehicle selectedVehicle = vehicle.getValue();
            vehicle.getItems().setAll(v);
            vehicle.setValue(v.stream().filter(x -> selectedVehicle != null && x.id().equals(selectedVehicle.id()))
                    .findFirst().orElse(v.isEmpty() ? null : v.get(0)));
        }).start();
        refreshHistory();
    }

    private Node build() {
        LocalDate today = LocalDate.now();
        Label title = new Label(messages.get("nav.reports"));
        title.getStyleClass().add("page-title");
        Label hint = new Label(messages.get("reports.hint"));
        hint.getStyleClass().add("page-subtitle");
        hint.setWrapText(true);
        VBox header = new VBox(4, title, hint);
        header.getStyleClass().add("page-header");

        account.setConverter(converter(FinancialAccount::name));
        account.setMaxWidth(Double.MAX_VALUE);
        from.setConverter(dateConverter());
        to.setConverter(dateConverter());
        from.setValue(today.withDayOfMonth(1));
        to.setValue(today);
        vehicle.setConverter(converter(Vehicle::displayName));
        vehicle.setMaxWidth(Double.MAX_VALUE);
        YearMonth current = YearMonth.now();
        closingsMonth = new MonthPicker(current, 36, messages.locale());
        collectionsMonth = new MonthPicker(current, 36, messages.locale());

        HBox dates = new HBox(8, labelled(messages.get("report.from"), from), labelled(messages.get("report.to"), to));
        Node statement = card(AppIcon.ACCOUNTS, messages.get("report.statement.title"),
                messages.get("report.statement.description"),
                new VBox(8, labelled(messages.get("transaction.account"), account), dates),
                () -> {
                    FinancialAccount a = account.getValue();
                    LocalDate f = from.getValue();
                    LocalDate t = to.getValue();
                    if (a == null || f == null || t == null || t.isBefore(f)) {
                        throw ValidationException.of(FieldError.of(null, "report.statement.invalid"));
                    }
                    return () -> reports.accountStatement(a.id(), f, t);
                });
        Node sheet = card(AppIcon.VEHICLE, messages.get("report.vehicle.title"),
                messages.get("report.vehicle.description"), labelled(messages.get("vehicles.column.vehicle"), vehicle),
                () -> {
                    Vehicle v = vehicle.getValue();
                    if (v == null) {
                        throw ValidationException.of(FieldError.of(null, "report.vehicle.required"));
                    }
                    return () -> reports.vehicleSheet(v.id());
                });
        Node closingsCard = card(AppIcon.LOCK, messages.get("report.closings.title"),
                messages.get("report.closings.description"), labelled(messages.get("period.month"), closingsMonth),
                () -> {
                    YearMonth m = closingsMonth.getValue();
                    return () -> reports.monthlyClosings(m);
                });
        Node collectionsCard = card(AppIcon.CALENDAR, messages.get("report.collections.title"),
                messages.get("report.collections.description"), labelled(messages.get("period.month"), collectionsMonth),
                () -> {
                    YearMonth m = collectionsMonth.getValue();
                    return () -> reports.monthlyCollections(m);
                });

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setPercentWidth(50);
            grid.getColumnConstraints().add(c);
        }
        grid.add(statement, 0, 0);
        grid.add(sheet, 1, 0);
        grid.add(closingsCard, 0, 1);
        grid.add(collectionsCard, 1, 1);

        Label recentTitle = new Label(messages.get("reports.history"));
        recentTitle.getStyleClass().add("panel-title");
        Label recentHint = new Label(messages.get("reports.history.hint"));
        recentHint.getStyleClass().add("muted-text");
        TextField search = new TextField();
        search.setPromptText(messages.get("reports.history.search"));
        HBox searchBox = new HBox(8, new FmsIcon(AppIcon.SEARCH, 16), search);
        searchBox.getStyleClass().add("search-box");
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox recentHeader = new HBox(8, new FmsIcon(AppIcon.PDF, 16), new VBox(2, recentTitle, recentHint), grow,
                searchBox);
        recentHeader.getStyleClass().add("panel-header");
        recentHeader.setAlignment(Pos.CENTER_LEFT);
        history = ui.tables().create();
        history.textColumn(messages.get("reports.column.generatedAt"),
                r -> ui.formats().dateTime(r.generatedAt()), 130).setSortable(false);
        history.textColumn(messages.get("reports.column.kind"), this::kindTitle, 230);
        history.textColumn(messages.get("reports.column.subject"), GeneratedReport::subject, 280);
        history.textColumn(messages.get("reports.column.reference"), GeneratedReport::reference, 170);
        history.textColumn(messages.get("reports.column.by"), GeneratedReport::generatedBy, 160);
        history.textColumn(messages.get("reports.column.size"), r -> FilesInput.sizeLabel(r.file().sizeBytes()), 70)
                .setMaxWidth(90);
        history.searchBy(r -> kindTitle(r) + " " + r.subject() + " " + r.reference() + " " + r.generatedBy());
        history.searchTextProperty().bind(search.textProperty());
        history.onOpen(this::preview);
        Label none = new Label(messages.get("reports.none"));
        none.getStyleClass().add("empty-text");
        history.placeholder(none);
        history.setPrefHeight(340);
        history.setMinHeight(220);
        Button open = new Button(messages.get("preview.open"), new FmsIcon(AppIcon.PREVIEW, 16));
        open.getStyleClass().add("secondary");
        open.disableProperty().bind(history.tableView().getSelectionModel().selectedItemProperty().isNull());
        open.setOnAction(e -> preview(history.tableView().getSelectionModel().getSelectedItem()));
        HBox recentActions = new HBox(open);
        recentActions.setAlignment(Pos.CENTER_RIGHT);
        VBox recentBody = new VBox(10, history, recentActions);
        recentBody.getStyleClass().add("panel-body");
        VBox recentPanel = new VBox(recentHeader, recentBody);
        recentPanel.getStyleClass().add("panel");

        VBox page = new VBox(16, header, grid, recentPanel);
        page.getStyleClass().addAll("page", "content");
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        return scroll;
    }

    private Node card(AppIcon icon, String title, String description, Node parameters,
                      Callable<Callable<GeneratedReport>> prepare) {
        FmsIcon i = new FmsIcon(icon, 20);
        Label t = new Label(title);
        t.getStyleClass().add("panel-title");
        HBox head = new HBox(10, i, t);
        head.setAlignment(Pos.CENTER_LEFT);
        Label d = new Label(description);
        d.getStyleClass().add("report-description");
        d.setWrapText(true);
        Button generate = new Button(messages.get("reports.generate"), new FmsIcon(AppIcon.PDF, 16));
        generate.getStyleClass().add("primary");
        generate.disableProperty().bind(busy);
        generate.setOnAction(e -> {
            Callable<GeneratedReport> work;
            try {
                work = prepare.call();
            } catch (ValidationException ve) {
                ui.dialogs().error(messages.get("crud.invalid.title"), messages.error(ve.errors().get(0)));
                return;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            ui.tasks().job(work).busy(busy).onSuccess(report -> {
                refreshHistory();
                preview(report);
            }).onFailure(err -> ui.dialogs().error(messages.get("action.failed.title"),
                    messages.get("reports.failed"))).start();
        });
        HBox actions = new HBox(generate);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox box = new VBox(12, head, d, parameters, actions);
        box.getStyleClass().addAll("panel", "report-card");
        VBox.setVgrow(d, Priority.ALWAYS);
        return box;
    }

    private void refreshHistory() {
        if (history == null) {
            return;
        }
        ui.tasks().job(reports::history).onSuccess(history::setItems).start();
    }

    private String kindTitle(GeneratedReport r) {
        return messages.get("report.kind." + r.kind().name());
    }

    /** Aperçu d'un rapport généré, avec « Télécharger ». */
    private void preview(GeneratedReport r) {
        if (r == null) {
            return;
        }
        previews.preview(kindTitle(r))
                .subtitle(r.subject() + " · " + messages.get("report.reference", r.reference()))
                .content(() -> reports.content(r))
                .downloadAs(r.file().fileName())
                .open();
    }

    private VBox labelled(String label, Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(4, l, control);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> f) {
        return new StringConverter<>() {
            @Override
            public String toString(T t) {
                return t == null ? "" : f.apply(t);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
    }

    private StringConverter<LocalDate> dateConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(LocalDate d) {
                return d == null ? "" : ui.formats().date(d);
            }

            @Override
            public LocalDate fromString(String s) {
                return s == null || s.isBlank() ? null : ui.formats().parseDate(s).orElse(null);
            }
        };
    }
}
