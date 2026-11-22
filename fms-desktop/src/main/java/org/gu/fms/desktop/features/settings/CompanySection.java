package org.gu.fms.desktop.features.settings;

import jakarta.inject.Inject;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.gu.fms.desktop.platform.ui.crud.CrudContext;
import org.gu.fms.desktop.platform.ui.form.Form;
import org.gu.fms.desktop.platform.ui.form.TextAreaInput;
import org.gu.fms.desktop.platform.ui.form.TextInput;
import org.gu.fms.desktop.platform.ui.icon.AppIcon;
import org.gu.fms.desktop.platform.ui.icon.FmsIcon;
import org.gu.fms.desktop.platform.ui.section.FormSection;
import org.gu.fms.domain.company.CompanyProfile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Paramètres › Général › Entreprise : identité reprise sur les documents. */
public final class CompanySection extends FormSection<CompanyProfile> {

    private final CompanyService service;
    private CompanyProfile loaded = CompanyProfile.EMPTY;
    private Path pendingLogo;
    private boolean logoRemoved;
    private TextInput name;
    private TextInput legalForm;
    private TextInput rccm;
    private TextInput niu;
    private TextAreaInput address;
    private TextInput city;
    private TextInput phone;
    private TextInput email;
    private final ImageView preview = new ImageView();
    private final Label logoName = new Label();
    private final StackPane previewBox = new StackPane();

    @Inject
    public CompanySection(CrudContext ui, CompanyService service) {
        super(ui);
        this.service = service;
    }

    @Override
    protected String title() {
        return messages.get("settings.company");
    }

    @Override
    protected String description() {
        return messages.get("company.description");
    }

    @Override
    protected Form buildForm() {
        Form form = ui.forms().create();
        name = form.text("name", messages.get("company.name")).required().maxLength(CompanyProfile.NAME_MAX).span(2);
        legalForm = form.text("legalForm", messages.get("company.legalForm")).maxLength(CompanyProfile.SHORT_MAX)
                .prompt(messages.get("company.legalForm.prompt"));
        city = form.text("city", messages.get("company.city")).maxLength(CompanyProfile.SHORT_MAX);
        rccm = form.text("rccm", messages.get("company.rccm")).maxLength(CompanyProfile.SHORT_MAX);
        niu = form.text("niu", messages.get("company.niu")).maxLength(CompanyProfile.SHORT_MAX);
        phone = form.text("phone", messages.get("company.phone")).maxLength(20);
        email = form.text("email", messages.get("company.email")).maxLength(CompanyProfile.EMAIL_MAX);
        address = form.textArea("address", messages.get("company.address")).maxLength(CompanyProfile.ADDRESS_MAX).rows(2);
        return form;
    }

    @Override
    protected Node extra() {
        Label title = new Label(messages.get("company.logo"));
        title.getStyleClass().add("form-section-title");
        preview.setFitWidth(72);
        preview.setFitHeight(72);
        preview.setPreserveRatio(true);
        previewBox.getStyleClass().add("logo-preview");
        previewBox.getChildren().setAll(new FmsIcon(AppIcon.IMAGE, 28));
        logoName.getStyleClass().add("field-message");
        Button choose = new Button(messages.get("company.logo.choose"), new FmsIcon(AppIcon.IMAGE, 16));
        choose.getStyleClass().add("secondary");
        choose.setOnAction(e -> chooseLogo(choose));
        Button remove = new Button(messages.get("company.logo.remove"), new FmsIcon(AppIcon.REMOVE, 16));
        remove.getStyleClass().add("secondary");
        remove.setOnAction(e -> {
            pendingLogo = null;
            logoRemoved = true;
            showLogo(null);
        });
        Label help = new Label(messages.get("company.logo.help"));
        help.getStyleClass().add("field-message");
        help.setWrapText(true);
        help.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox right = new VBox(8, new HBox(8, choose, remove), logoName, help);
        HBox row = new HBox(16, previewBox, right);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(10, title, row);
    }

    private void chooseLogo(Button owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("company.logo.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("company.logo.filter"),
                "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(owner.getScene().getWindow());
        if (file != null) {
            pendingLogo = file.toPath();
            logoRemoved = false;
            showLogo(pendingLogo);
        }
    }

    private void showLogo(Path path) {
        if (path != null && Files.isRegularFile(path)) {
            preview.setImage(new Image(path.toUri().toString(), 72, 72, true, true, true));
            previewBox.getChildren().setAll(preview);
            logoName.setText(path.getFileName().toString());
        } else {
            preview.setImage(null);
            previewBox.getChildren().setAll(new FmsIcon(AppIcon.IMAGE, 28));
            logoName.setText(messages.get("company.logo.none"));
        }
    }

    @Override
    protected boolean extraDirty() {
        return pendingLogo != null || (logoRemoved && loaded.logoFile() != null);
    }

    @Override
    protected CompanyProfile load() {
        return service.load();
    }

    @Override
    protected void fill(CompanyProfile p) {
        loaded = p;
        pendingLogo = null;
        logoRemoved = false;
        name.setValue(p.name());
        legalForm.setValue(p.legalForm());
        rccm.setValue(p.rccm());
        niu.setValue(p.niu());
        address.setValue(p.address());
        city.setValue(p.city());
        phone.setValue(p.phone());
        email.setValue(p.email());
        showLogo(service.logoPath(p));
    }

    @Override
    protected CompanyProfile collect() {
        String logo = logoRemoved ? null : loaded.logoFile();
        return new CompanyProfile(name.getValue(), legalForm.getValue(), rccm.getValue(), niu.getValue(),
                address.getValue(), city.getValue(), phone.getValue(), email.getValue(), logo);
    }

    @Override
    protected void save(CompanyProfile p) {
        loaded = service.save(p, pendingLogo);
    }

    @Override
    protected void afterSave(CompanyProfile saved) {
        pendingLogo = null;
        logoRemoved = false;
    }

    @Override
    protected String savedMessage() {
        return messages.get("company.saved");
    }
}
