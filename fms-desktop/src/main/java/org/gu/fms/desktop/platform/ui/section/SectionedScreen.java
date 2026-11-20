package org.gu.fms.desktop.platform.ui.section;

import com.google.inject.Injector;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Screen;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Écran composé de plusieurs sous-écrans (onglets ou navigation latérale) : Chauffeurs | Affectations,
 * Paramètres › Entreprise / Flotte / Paie…
 * <p>
 * Chaque section est un {@link Screen} ordinaire, créé par Guice à sa première ouverture puis conservé.
 * Avant de changer de section (ou de quitter l'écran), la section courante est consultée
 * ({@link Screen#confirmLeave()}) : un formulaire modifié n'est jamais abandonné sans confirmation.
 * Les sections qui le permettent sont affichées sans marge ni titre de page ({@link Screen.Embeddable}).
 */
public abstract class SectionedScreen implements Screen {

    public enum Layout {
        /** Onglets horizontaux sous le titre. */
        TABS,
        /** Liste verticale à gauche, regroupée par rubriques. */
        SIDE
    }

    /**
     * @param groupKey clé du titre de rubrique (navigation latérale) ; null = pas de rubrique
     */
    public record Section(String groupKey, String titleKey, AppIcon icon, Class<? extends Screen> screen) {
        public Section {
            Objects.requireNonNull(titleKey, "titleKey");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(screen, "screen");
        }
    }

    protected final Messages messages;
    private final Injector injector;
    private final Map<Integer, Screen> screens = new HashMap<>();
    private final List<ToggleButton> buttons = new ArrayList<>();
    private final ToggleGroup group = new ToggleGroup();
    private final StackPane host = new StackPane();
    private List<Section> sections;
    private int current = -1;
    private Node view;

    protected SectionedScreen(Injector injector, Messages messages) {
        this.injector = injector;
        this.messages = messages;
    }

    protected abstract String titleKey();

    protected abstract Layout layout();

    protected abstract List<Section> sections();

    @Override
    public final Node view() {
        if (view == null) {
            sections = List.copyOf(sections());
            view = build();
            select(0);
        }
        return view;
    }

    @Override
    public void onShow() {
        if (current >= 0) {
            screen(current).onShow();
        }
    }

    @Override
    public CompletableFuture<Boolean> confirmLeave() {
        return current < 0 ? CompletableFuture.completedFuture(true) : screen(current).confirmLeave();
    }

    /** Ouvre une section par sa clé de titre (ex. depuis un lien d'un autre écran). */
    public void open(String titleKey) {
        view();
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).titleKey().equals(titleKey)) {
                request(i);
                return;
            }
        }
    }

    private Node build() {
        Label title = new Label(messages.get(titleKey()));
        title.getStyleClass().add("page-title");
        host.getStyleClass().add("section-host");
        VBox.setVgrow(host, Priority.ALWAYS);
        HBox.setHgrow(host, Priority.ALWAYS);

        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            ToggleButton b = new ToggleButton(messages.get(s.titleKey()), new FmsIcon(s.icon(), 16));
            b.setToggleGroup(group);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            int index = i;
            b.setOnAction(e -> {
                b.setSelected(index == current);        // l'état visuel suit la section réellement affichée
                request(index);
            });
            buttons.add(b);
        }

        VBox page = new VBox(16);
        page.getStyleClass().addAll("page", "content", "sectioned-page");
        if (layout() == Layout.TABS) {
            HBox tabs = new HBox(4);
            tabs.getStyleClass().add("tabs");
            buttons.forEach(b -> {
                b.getStyleClass().add("tab-button");
                tabs.getChildren().add(b);
            });
            page.getChildren().addAll(title, tabs, host);
        } else {
            VBox nav = new VBox(2);
            nav.getStyleClass().add("section-nav");
            String lastGroup = null;
            for (int i = 0; i < sections.size(); i++) {
                Section s = sections.get(i);
                if (s.groupKey() != null && !s.groupKey().equals(lastGroup)) {
                    Label g = new Label(messages.get(s.groupKey()));
                    g.getStyleClass().add("section-group");
                    nav.getChildren().add(g);
                    lastGroup = s.groupKey();
                }
                buttons.get(i).getStyleClass().add("section-button");
                nav.getChildren().add(buttons.get(i));
            }
            ScrollPane navScroll = new ScrollPane(nav);
            navScroll.setFitToWidth(true);
            navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            navScroll.getStyleClass().add("section-nav-scroll");
            HBox body = new HBox(20, navScroll, host);
            VBox.setVgrow(body, Priority.ALWAYS);
            page.getChildren().addAll(title, body);
        }
        return page;
    }

    private void request(int index) {
        if (index == current) {
            return;
        }
        CompletableFuture<Boolean> canLeave = current < 0
                ? CompletableFuture.completedFuture(true) : screen(current).confirmLeave();
        canLeave.thenAccept(ok -> {
            if (ok) {
                select(index);
            }
        });
    }

    private void select(int index) {
        Screen s = screen(index);
        host.getChildren().setAll(s.view());
        current = index;
        buttons.get(index).setSelected(true);
        s.onShow();
    }

    private Screen screen(int index) {
        return screens.computeIfAbsent(index, i -> {
            Screen s = injector.getInstance(sections.get(i).screen());
            if (s instanceof Screen.Embeddable e) {
                e.embed();
            }
            return s;
        });
    }
}
