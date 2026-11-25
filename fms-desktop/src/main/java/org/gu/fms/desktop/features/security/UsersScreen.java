package org.gu.fms.desktop.features.security;

import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.session.UserSession;
import jakarta.inject.Inject;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import org.gu.fms.desktop.platform.ui.Tone;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.crud.CrudScreen;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.nav.Route;
import org.gu.fms.desktop.platform.ui.nav.RouteRegistry;
import org.gu.fms.desktop.platform.ui.table.Badge;
import org.gu.fms.desktop.platform.ui.table.DataTable;
import org.gu.fms.domain.security.AppUser;
import org.gu.fms.domain.security.UserRole;
import org.gu.fms.domain.security.UserRules;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Paramètres › Utilisateurs (propriétaire seulement). Le propriétaire a accès à tout ; pour un utilisateur, on coche
 * les modules ouverts. Un nouvel utilisateur reçoit un mot de passe provisoire à changer à sa première connexion.
 */
public final class UsersScreen extends CrudScreen<AppUser> {

    private final UserService service;
    private final AuthService auth;
    private final UserSession session;
    private final List<Route> modules;

    @Inject
    public UsersScreen(CrudContext ui, UserService service, AuthService auth, UserSession session, RouteRegistry routes) {
        super(ui, service);
        this.service = service;
        this.auth = auth;
        this.session = session;
        this.modules = routes.all().stream().filter(r -> !UserSession.OWNER_ONLY_MODULES.contains(r.id())).toList();
    }

    @Override
    protected String title() {
        return messages.get("settings.users");
    }

    @Override
    protected String countText(int visible, int total) {
        return visible == total ? messages.get("users.count", total) : messages.get("users.countFiltered", visible, total);
    }

    @Override
    protected String hint() {
        return messages.get("users.hint");
    }

    @Override
    protected String createLabel() {
        return messages.get("user.create");
    }

    @Override
    protected AppIcon icon() {
        return AppIcon.USERS;
    }

    @Override
    protected String emptyTitle() {
        return messages.get("users.empty.title");
    }

    @Override
    protected String emptyText() {
        return messages.get("users.empty.text");
    }

    private String moduleNames(AppUser u) {
        if (u.isOwner()) {
            return messages.get("user.modules.all");
        }
        return modules.stream().filter(r -> u.modules().contains(r.id())).map(r -> messages.get(r.titleKey()))
                .collect(Collectors.joining(", "));
    }

    @Override
    protected void configureTable(DataTable<AppUser> table) {
        table.textColumn(messages.get("user.displayName"), AppUser::displayName, 180).setMinWidth(140);
        table.textColumn(messages.get("auth.username"), AppUser::username, 130);
        table.badgeColumn(messages.get("user.role"), u -> new Badge(messages.label(u.role()),
                u.isOwner() ? Tone.INFO : Tone.NEUTRAL), 120);
        table.textColumn(messages.get("user.modules"), this::moduleNames, 320).setMinWidth(200);
        table.textColumn(messages.get("user.lastLogin"),
                u -> u.lastLoginAt() == null ? "—" : ui.formats().dateTime(u.lastLoginAt()), 150);
        table.badgeColumn(messages.get("common.state"), u -> !u.active()
                ? new Badge(messages.get("common.inactive"), Tone.NEUTRAL)
                : u.mustChangePassword() ? new Badge(messages.get("user.mustChange"), Tone.WARNING)
                : new Badge(messages.get("common.active"), Tone.SUCCESS), 150);
        table.searchBy(u -> u.displayName() + " " + u.username() + " " + messages.label(u.role()));
    }

    @Override
    protected List<Button> headerActions() {
        Button key = new Button(messages.get("auth.recovery.regenerate"), new FmsIcon(AppIcon.KEY, 16));
        key.getStyleClass().add("secondary");
        key.setOnAction(e -> ui.dialogs().confirm(messages.get("auth.recovery.regenerate.title"),
                        messages.get("auth.recovery.regenerate.message"), messages.get("auth.recovery.regenerate"),
                        Tone.WARNING)
                .thenAccept(ok -> {
                    if (ok) {
                        ui.run(auth::regenerateRecoveryKey, null, k -> ui.dialogs().alert(Tone.INFO,
                                messages.get("auth.recovery.newTitle"), messages.get("auth.recovery.shown", k)));
                    }
                }));
        return List.of(key);
    }

    @Override
    protected List<Button> editorActions(AppUser existing) {
        Button reset = new Button(messages.get("user.resetPassword"), new FmsIcon(AppIcon.KEY, 16));
        reset.getStyleClass().add("secondary");
        reset.setOnAction(e -> closeEditor().thenAccept(closed -> {
            if (closed) {
                openReset(existing);
            }
        }));
        return List.of(reset);
    }

    private void openReset(AppUser user) {
        Form form = ui.forms().create();
        PasswordInput password = form.password("password", messages.get("user.temporaryPassword")).required()
                .help(messages.get("user.password.rule"));
        ui.<Boolean>formDrawer(messages.get("user.resetPassword"))
                .subtitle(user.displayName())
                .form(form)
                .onSave(() -> {
                    char[] p = password.getValue().toCharArray();
                    return () -> {
                        service.resetPassword(user.id(), p);
                        return Boolean.TRUE;
                    };
                })
                .onSuccess(ok -> {
                    ui.toasts().success(messages.get("user.passwordReset", user.displayName()));
                    refresh();
                })
                .open();
    }

    @Override
    protected Callable<AppUser> saveWork(EntityEditor<AppUser> editor, AppUser entity, boolean creating) {
        if (creating && editor instanceof Editor e) {
            char[] p = e.password.getValue() == null ? new char[0] : e.password.getValue().toCharArray();
            return () -> service.create(entity, p);
        }
        return () -> service.update(entity);
    }

    @Override
    protected EntityEditor<AppUser> createEditor(AppUser existing) {
        return new Editor(existing);
    }

    @Override
    protected String savedMessage(AppUser saved, boolean created) {
        return messages.get(created ? "user.created" : "user.updated", saved.displayName());
    }

    @Override
    protected Object identityOf(AppUser u) {
        return u.id();
    }

    private final class Editor implements EntityEditor<AppUser> {
        private final AppUser existing;
        private final Form form;
        private final TextInput displayName;
        private final TextInput username;
        private final ChoiceInput<UserRole> role;
        private final CheckInput active;
        private final MultiChoiceInput<Route> allowed;
        private final PasswordInput password;

        Editor(AppUser existing) {
            this.existing = existing;
            Messages m = messages;
            form = ui.forms().create();
            displayName = form.text("displayName", m.get("user.displayName")).required().maxLength(UserRules.NAME_MAX);
            username = form.text("username", m.get("auth.username")).required().maxLength(UserRules.USERNAME_MAX)
                    .onBlur(s -> s == null ? null : s.toLowerCase(java.util.Locale.ROOT))
                    .help(m.get("user.username.help"));
            role = form.choice("role", m.get("user.role"), UserRole.class).required().help(m.get("user.role.help"));
            active = form.check("active", m.get("user.active"));
            allowed = form.multi("modules", m.get("user.modules"), modules, r -> m.get(r.titleKey())).span(2)
                    .help(m.get("user.modules.help"));
            allowed.shownWhen(Bindings.createBooleanBinding(() -> role.getValue() == UserRole.USER, role.valueProperty()));
            if (existing == null) {
                password = form.password("password", m.get("user.temporaryPassword")).required()
                        .help(m.get("user.temporaryPassword.help"));
                role.setValue(UserRole.USER);
                active.setValue(true);
                active.readOnly(true);
            } else {
                password = null;
                displayName.setValue(existing.displayName());
                username.setValue(existing.username());
                role.setValue(existing.role());
                active.setValue(existing.active());
                Set<String> ids = existing.modules();
                allowed.setValue(modules.stream().filter(r -> ids.contains(r.id())).toList());
                boolean me = existing.id().equals(session.currentUser().id());
                if (me) {
                    role.readOnly(true);
                    active.readOnly(true);
                    role.help(m.get("user.notYourself.help"));
                }
            }
        }

        @Override
        public String title() {
            return existing == null ? messages.get("user.create") : messages.get("user.edit", existing.displayName());
        }

        @Override
        public Form form() {
            return form;
        }

        @Override
        public AppUser toEntity() {
            Set<String> ids = allowed.getValue().stream().map(Route::id).collect(Collectors.toSet());
            return new AppUser(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                    username.getValue(), displayName.getValue(), role.getValue(), active.getValue(),
                    existing != null && existing.mustChangePassword(), 0, null, null, ids);
        }
    }
}
