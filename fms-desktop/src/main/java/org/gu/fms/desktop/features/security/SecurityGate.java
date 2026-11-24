package org.gu.fms.desktop.features.security;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.gu.fms.desktop.platform.brand.BrandAssets;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.overlay.OverlayHost;
import org.gu.fms.desktop.platform.ui.task.UiTasks;
import org.gu.fms.domain.common.ValidationException;

/**
 * Écran de connexion, plein cadre au-dessus de la coque (rien n'est visible ni utilisable avant la connexion).
 * <p>
 * Parcours : connexion → (identifiants par défaut, aucun utilisateur) création du propriétaire → clé de récupération
 * à noter → application ; (mot de passe provisoire) changement obligatoire → application. « Mot de passe oublié » :
 * clé de récupération → nouveau mot de passe → nouvelle clé.
 */
@Singleton
public final class SecurityGate {

    private final AuthService auth;
    private final OverlayHost overlays;
    private final UiTasks tasks;
    private final Messages messages;
    private final BooleanProperty busy = new SimpleBooleanProperty();
    private StackPane layer;
    private VBox card;
    private Runnable onSignedIn;

    @Inject
    public SecurityGate(AuthService auth, OverlayHost overlays, UiTasks tasks, Messages messages) {
        this.auth = auth;
        this.overlays = overlays;
        this.tasks = tasks;
        this.messages = messages;
    }

    /** Affiche l'écran de connexion ; {@code then} est appelé une fois l'utilisateur connecté (sur le fil JavaFX). */
    public void require(Runnable then) {
        this.onSignedIn = then;
        if (layer == null) {
            card = new VBox(14);
            card.getStyleClass().add("login-card");
            card.setMaxWidth(420);
            card.setMaxHeight(Region.USE_PREF_SIZE);
            layer = new StackPane(card);
            layer.getStyleClass().addAll("login-layer");
            StackPane.setAlignment(card, Pos.CENTER);
        }
        if (layer.getParent() == null) {
            overlays.push(layer);
        }
        showLogin(null);
    }

    private void finish() {
        overlays.remove(layer);
        if (onSignedIn != null) {
            onSignedIn.run();
        }
    }

    // ---- Écrans ------------------------------------------------------------------------------------------------

    private void showLogin(String info) {
        TextField username = new TextField();
        username.setPromptText(messages.get("auth.username"));
        PasswordField password = new PasswordField();
        password.setPromptText(messages.get("auth.password"));
        Label error = errorLabel();
        Button submit = primary(messages.get("auth.signIn"));
        Hyperlink forgot = new Hyperlink(messages.get("auth.forgot"));
        forgot.setOnAction(e -> showForgot());
        VBox body = new VBox(10, field(messages.get("auth.username"), username),
                field(messages.get("auth.password"), password), error, submit);
        tasks.job(auth::hasUsers).onSuccess(has -> {
            if (!has) {
                Label first = note(messages.get("auth.firstUse", AuthService.DEFAULT_USERNAME,
                        AuthService.DEFAULT_PASSWORD));
                body.getChildren().add(0, first);
                forgot.setVisible(false);
            }
        }).start();
        if (info != null) {
            body.getChildren().add(0, note(info));
        }
        Runnable go = () -> run(error, () -> auth.login(username.getText(), password.getText().toCharArray()), login -> {
            password.clear();
            if (login.user().bootstrap()) {
                showFirstOwner();
            } else if (login.mustChangePassword()) {
                showChangePassword();
            } else {
                finish();
            }
        });
        submit.setOnAction(e -> go.run());
        password.setOnAction(e -> go.run());
        username.setOnAction(e -> password.requestFocus());
        setCard(messages.get("auth.title"), messages.get("auth.subtitle"), body, forgot);
        username.requestFocus();
    }

    private void showFirstOwner() {
        TextField displayName = new TextField();
        TextField username = new TextField();
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        Label error = errorLabel();
        Button submit = primary(messages.get("auth.firstOwner.create"));
        VBox body = new VBox(10, note(messages.get("auth.firstOwner.text")),
                field(messages.get("user.displayName"), displayName), field(messages.get("auth.username"), username),
                field(messages.get("auth.newPassword"), password), field(messages.get("auth.confirm"), confirm),
                note(messages.get("user.password.rule")), error, submit);
        submit.setOnAction(e -> run(error, () -> auth.createFirstOwner(username.getText(), displayName.getText(),
                password.getText().toCharArray(), confirm.getText().toCharArray()), this::showRecoveryKey));
        setCard(messages.get("auth.firstOwner.title"), null, body, null);
        displayName.requestFocus();
    }

    private void showRecoveryKey(String key) {
        TextField shown = new TextField(key);
        shown.setEditable(false);
        shown.getStyleClass().add("recovery-key");
        Button copy = new Button(messages.get("auth.recovery.copy"));
        copy.getStyleClass().add("secondary");
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(key);
            Clipboard.getSystemClipboard().setContent(c);
        });
        HBox row = new HBox(8, shown, copy);
        HBox.setHgrow(shown, Priority.ALWAYS);
        CheckBox noted = new CheckBox(messages.get("auth.recovery.noted"));
        Button next = primary(messages.get("auth.recovery.continue"));
        next.disableProperty().bind(noted.selectedProperty().not());
        next.setOnAction(e -> finish());
        VBox body = new VBox(12, note(messages.get("auth.recovery.text")), row, noted, next);
        setCard(messages.get("auth.recovery.title"), null, body, null);
    }

    private void showChangePassword() {
        PasswordField current = new PasswordField();
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        Label error = errorLabel();
        Button submit = primary(messages.get("auth.changePassword.submit"));
        VBox body = new VBox(10, note(messages.get("auth.changePassword.forced")),
                field(messages.get("auth.currentPassword"), current), field(messages.get("auth.newPassword"), password),
                field(messages.get("auth.confirm"), confirm), note(messages.get("user.password.rule")), error, submit);
        submit.setOnAction(e -> run(error, () -> {
            auth.changePassword(current.getText().toCharArray(), password.getText().toCharArray(),
                    confirm.getText().toCharArray());
            return Boolean.TRUE;
        }, ok -> finish()));
        setCard(messages.get("auth.changePassword.title"), null, body, null);
        current.requestFocus();
    }

    private void showForgot() {
        TextField username = new TextField();
        TextField key = new TextField();
        key.setPromptText("XXXX-XXXX-XXXX-XXXX-XXXX");
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        Label error = errorLabel();
        Button submit = primary(messages.get("auth.forgot.submit"));
        Hyperlink back = new Hyperlink(messages.get("auth.backToLogin"));
        back.setOnAction(e -> showLogin(null));
        VBox body = new VBox(10, note(messages.get("auth.forgot.text")),
                field(messages.get("auth.ownerUsername"), username), field(messages.get("auth.recoveryKey"), key),
                field(messages.get("auth.newPassword"), password), field(messages.get("auth.confirm"), confirm),
                error, submit);
        submit.setOnAction(e -> run(error, () -> auth.resetWithRecoveryKey(username.getText(), key.getText(),
                password.getText().toCharArray(), confirm.getText().toCharArray()), newKey -> {
            showNewKeyThenLogin(newKey);
        }));
        setCard(messages.get("auth.forgot.title"), null, body, back);
        username.requestFocus();
    }

    private void showNewKeyThenLogin(String key) {
        TextField shown = new TextField(key);
        shown.setEditable(false);
        shown.getStyleClass().add("recovery-key");
        CheckBox noted = new CheckBox(messages.get("auth.recovery.noted"));
        Button next = primary(messages.get("auth.backToLogin"));
        next.disableProperty().bind(noted.selectedProperty().not());
        next.setOnAction(e -> showLogin(messages.get("auth.forgot.done")));
        setCard(messages.get("auth.recovery.newTitle"), null,
                new VBox(12, note(messages.get("auth.recovery.newText")), shown, noted, next), null);
    }

    // ---- Construction ----------------------------------------------------------------------------------------------

    private void setCard(String title, String subtitle, Node body, Node footer) {
        Node logo = BrandAssets.logo(128)
                .map(b -> new Image(new ByteArrayInputStream(b), 56, 56, true, true))
                .filter(i -> !i.isError())
                .<Node>map(ImageView::new)
                .orElseGet(() -> {
                    Label mark = new Label("FMS");
                    mark.getStyleClass().add("brand-logo-fallback");
                    return mark;
                });
        Label t = new Label(title);
        t.getStyleClass().add("login-title");
        t.setWrapText(true);
        VBox head = new VBox(6, logo, t);
        head.setAlignment(Pos.CENTER);
        if (subtitle != null) {
            Label s = new Label(subtitle);
            s.getStyleClass().add("login-subtitle");
            head.getChildren().add(s);
        }
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("inline-spinner");
        spinner.visibleProperty().bind(busy);
        spinner.managedProperty().bind(busy);
        card.getChildren().setAll(head, body, spinner);
        if (footer != null) {
            HBox f = new HBox(footer);
            f.setAlignment(Pos.CENTER);
            card.getChildren().add(f);
        }
        Label lock = new Label(messages.get("auth.localOnly"), new FmsIcon(AppIcon.LOCK, 14));
        lock.getStyleClass().add("login-footnote");
        lock.setWrapText(true);
        card.getChildren().add(lock);
    }

    private static VBox field(String label, Node input) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        return new VBox(4, l, input);
    }

    private static Label note(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("login-note");
        l.setWrapText(true);
        return l;
    }

    private static Label errorLabel() {
        Label l = new Label();
        l.getStyleClass().add("login-error");
        l.setWrapText(true);
        l.managedProperty().bind(l.textProperty().isNotEmpty());
        l.visibleProperty().bind(l.managedProperty());
        return l;
    }

    private Button primary(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("primary");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setDefaultButton(true);
        b.disableProperty().bind(busy);
        return b;
    }

    /** Hachage PBKDF2 coûteux : hors du fil JavaFX ; erreurs métier affichées sous le formulaire. */
    private <T> void run(Label error, Callable<T> work, java.util.function.Consumer<T> then) {
        error.setText("");
        tasks.job(work).busy(busy).onSuccess(then).onFailure(e -> error.setText(describe(e))).start();
    }

    private String describe(Throwable e) {
        Throwable t = e;
        while (t != null && !(t instanceof ValidationException)) {
            t = t.getCause();
        }
        if (t instanceof ValidationException v) {
            return v.errors().stream().map(messages::error).collect(Collectors.joining("\n"));
        }
        System.getLogger(SecurityGate.class.getName()).log(System.Logger.Level.ERROR, "Connexion impossible", e);
        return messages.get("crud.failed.text");
    }
}