package org.gu.fms.desktop.platform.ui.table;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.format.Formats;
import org.gu.fms.domain.money.Money;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DataTable<T> extends VBox {
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");

    private final Formats formats;

    private final ObservableList<T> source = FXCollections.observableArrayList();
    private final FilteredList<T> filtered = new FilteredList<>(source);
    private final SortedList<T> sorted = new SortedList<>(filtered);
    private final TableView<T> table = new TableView<>(sorted);
    private final StringProperty searchText = new SimpleStringProperty(this, "searchText", "");
    private final ObjectProperty<Predicate<T>> filter = new SimpleObjectProperty<>(this, "filter", t -> true);
    private final ReadOnlyIntegerWrapper visibleCount = new ReadOnlyIntegerWrapper(this, "visibleCount");
    private final ReadOnlyIntegerWrapper totalCount = new ReadOnlyIntegerWrapper(this, "totalCount");
    private Function<T, String> searchable = Objects::toString;
    private Consumer<T> onOpen = t -> {};

    DataTable(Formats formats) {
        this.formats = formats;
        getStyleClass().add("data-table");
        table.getStyleClass().add("data-table-view");
        table.setColumnResizePolicy(ResizePolicies.fill());
        sorted.comparatorProperty().bind(table.comparatorProperty());
        filtered.predicateProperty().bind(Bindings.createObjectBinding(() -> {
            String query = normalize(searchText.get());
            Predicate<T> extra = filter.get();
            return item -> extra.test(item) && (query.isEmpty() || normalize(searchable.apply(item)).contains(query));
        }, searchText, filter));
        visibleCount.bind(Bindings.size(filtered));
        totalCount.bind(Bindings.size(source));

        table.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    onOpen.accept(row.getItem());
                }
            });
            return row;
        });
        table.setOnKeyPressed(e -> {
            T selected = table.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && selected != null) {
                e.consume();
                onOpen.accept(selected);
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().add(table);
    }

    // ---- Colonnes -------------------------------------------------------------------------

    public TableColumn<T, String> textColumn(String title, Function<T, String> value, double width) {
        TableColumn<T, String> c = column(title, value, width);
        c.setComparator(Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        return c;
    }

    public TableColumn<T, Money> moneyColumn(String title, Function<T, Money> value, double width) {
        TableColumn<T, Money> c = column(title, value, width);
        c.getStyleClass().add("numeric");
        c.setCellFactory(col -> textCell(formats::amount, "numeric"));
        c.setComparator(Comparator.nullsFirst(Comparator.comparingLong(Money::minor)));
        return c;
    }

    private <V> TableColumn<T, V> column(String title, Function<T, V> value, double width) {
        TableColumn<T, V> c = new TableColumn<>(title);
        c.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(value.apply(data.getValue())));
        c.setPrefWidth(width);
        c.setMinWidth(Math.min(width, 80));
        c.setReorderable(false);
        table.getColumns().add(c);
        return c;
    }

    public TableColumn<T, Long> numberColumn(String title, Function<T, Long> value, String unit, double width) {
        TableColumn<T, Long> c = column(title, value, width);
        c.getStyleClass().add("numeric");
        c.setCellFactory(col -> textCell(v -> unit == null ? formats.number(v) : formats.number(v) + " " + unit,
                "numeric"));
        c.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        return c;
    }

    public TableColumn<T, LocalDate> dateColumn(String title, Function<T, LocalDate> value, double width) {
        TableColumn<T, LocalDate> c = column(title, value, width);
        c.setCellFactory(col -> textCell(formats::date, "date"));
        c.setComparator(Comparator.nullsFirst(Comparator.naturalOrder()));
        return c;
    }

    public TableColumn<T, Badge> badgeColumn(String title, Function<T, Badge> value, double width) {
        TableColumn<T, Badge> c = column(title, value, width);
        c.setCellFactory(col -> new TableCell<>() {
            private final Label chip = new Label();

            @Override
            protected void updateItem(Badge badge, boolean empty) {
                super.updateItem(badge, empty);
                if (empty || badge == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(badge.text());
                chip.getStyleClass().setAll("label", "chip", badge.tone().styleClass());
                setGraphic(chip);
            }
        });
        c.setComparator(Comparator.nullsLast(Comparator.comparing(Badge::text, String.CASE_INSENSITIVE_ORDER)));
        return c;
    }

    private <V> TableCell<T, V> textCell(Function<V, String> format, String styleClass) {
        TableCell<T, V> cell = new TableCell<>() {
            @Override
            protected void updateItem(V item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        };
        cell.getStyleClass().add(styleClass);
        if ("numeric".equals(styleClass)) {
            cell.setAlignment(Pos.CENTER_RIGHT);
        }
        return cell;
    }

    // ---- Données, recherche, filtre --------------------------------------------------------

    public void setItems(Collection<T> items) {
        T selected = table.getSelectionModel().getSelectedItem();
        source.setAll(items);
        if (selected != null) {
            select(selected);
        }
    }

    public ObservableList<T> items() {
        return source;
    }

    /** Texte sur lequel porte la recherche (casse et accents ignorés). */
    public DataTable<T> searchBy(Function<T, String> text) {
        this.searchable = text;
        return this;
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    /** Filtre supplémentaire (ex. statut), combiné à la recherche. */
    public ObjectProperty<Predicate<T>> filterProperty() {
        return filter;
    }

    public DataTable<T> onOpen(Consumer<T> handler) {
        this.onOpen = handler;
        return this;
    }

    public DataTable<T> placeholder(Node node) {
        table.setPlaceholder(node);
        return this;
    }

    /** Sélectionne la ligne égale à {@code item} (même identité métier) et la rend visible. */
    public void select(T item) {
        int index = sorted.indexOf(item);
        if (index >= 0) {
            table.getSelectionModel().clearAndSelect(index);
            table.scrollTo(index);
        }
    }

    /** Sélectionne la première ligne qui vérifie le prédicat. */
    public void selectFirst(Predicate<T> predicate) {
        sorted.stream().filter(predicate).findFirst().ifPresent(this::select);
    }

    public ReadOnlyIntegerProperty visibleCountProperty() {
        return visibleCount.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty totalCountProperty() {
        return totalCount.getReadOnlyProperty();
    }

    public TableView<T> tableView() {
        return table;
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return noAccents.toLowerCase(Locale.ROOT).strip();
    }

}
