package org.gu.fms.desktop.app;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.desktop.features.alert.AlertCenter;
import org.gu.fms.desktop.features.alert.AlertGate;
import org.gu.fms.desktop.features.security.AuthService;
import org.gu.fms.desktop.features.security.SecurityGate;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.form.PasswordInput;
import org.gu.fms.desktop.platform.ui.nav.Navigator;
import org.gu.fms.desktop.platform.ui.nav.Route;

/**
 * Cycle de session : connexion → premier écran accessible → contrôle des alertes critiques ; déconnexion → plus
 * rien d'affiché, retour à la connexion ; changement de mot de passe depuis l'espace utilisateur.
 */
@Singleton
public final class SessionFlow {

    private final SecurityGate gate;
    private final AuthService auth;
    private final Navigator navigator;
    private final AlertGate alertGate;
    private final AlertCenter alerts;
    private final CrudContext ui;
    private final Messages messages;

    @Inject
    public SessionFlow(SecurityGate gate, AuthService auth, Navigator navigator, AlertGate alertGate,
                       AlertCenter alerts, CrudContext ui, Messages messages) {
        this.gate = gate;
        this.auth = auth;
        this.navigator = navigator;
        this.alertGate = alertGate;
        this.alerts = alerts;
        this.ui = ui;
        this.messages = messages;
    }

    /** Démarrage : écran de connexion. */
    public void start() {
        alertGate.install();
        gate.require(this::enter);
    }

    private void enter() {
        Route first = navigator.firstAccessible().orElse(null);
        if (first != null) {
            navigator.navigate(first).thenRun(alertGate::checkNow);
        } else {
            alertGate.checkNow();
        }
    }

    public void logout() {
        Route current = navigator.getCurrent();
        var leave = current == null ? java.util.concurrent.CompletableFuture.completedFuture(true)
                : ui.dialogs().confirm(messages.get("auth.logout.title"), messages.get("auth.logout.message"),
                messages.get("auth.logout"), Tone.NEUTRAL);
        leave.thenAccept(ok -> {
            if (ok) {
                alertGate.reset();
                navigator.reset();
                auth.logout();
                alerts.refresh();
                gate.require(this::enter);
            }
        });
    }

    public void changePassword() {
        Form form = ui.forms().create();
        PasswordInput current = form.password("current", messages.get("auth.currentPassword")).required();
        PasswordInput password = form.password("password", messages.get("auth.newPassword")).required()
                .help(messages.get("user.password.rule"));
        PasswordInput confirm = form.password("confirmation", messages.get("auth.confirm")).required();
        ui.<Boolean>formDrawer(messages.get("auth.changePassword.title"))
                .form(form)
                .onSave(() -> {
                    char[] c = current.getValue().toCharArray();
                    char[] p = password.getValue().toCharArray();
                    char[] k = confirm.getValue().toCharArray();
                    return () -> {
                        auth.changePassword(c, p, k);
                        return Boolean.TRUE;
                    };
                })
                .onSuccess(ok -> ui.toasts().success(messages.get("auth.changePassword.done")))
                .open();
    }
}
