package org.gu.fms.desktop.features.vehicle;

import javafx.scene.Node;
import org.gu.fms.desktop.features.document.DocumentDraft;
import org.gu.fms.desktop.features.document.DocumentDrafts;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.money.Money;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.Vehicle.AcquisitionMethod;
import org.gu.fms.domain.vehicle.VehicleCategory;
import org.gu.fms.domain.vehicle.VehicleRules;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Formulaire de création / modification d'un véhicule. */
final class VehicleEditor implements EntityEditor<Vehicle> {

    private static final Pattern REGISTRATION = Pattern.compile("[A-Za-z0-9 -]+");

    private final Messages messages;
    private final Vehicle existing;
    private final Form form;
    private final TextInput registration;
    private final List<VehicleCategory> categories;
    private final Money defaultThreshold;
    private final ChoiceInput<VehicleCategory> category;
    private final TextInput make;
    private final TextInput model;
    private final ChoiceInput<Vehicle.VehicleStatus> status;
    private final DateInput acquisitionDate;
    private final ChoiceInput<AcquisitionMethod> acquisitionMethod;
    private final MoneyInput purchasePrice;
    private final NumberInput currentMileage;
    private final MoneyInput repairThreshold;
    private final TextAreaInput notes;
    private final DocumentDrafts drafts;
    private final Node documentsPanel;

    /**
     * @param categories       toutes les catégories ; seules les actives (et celle du véhicule) sont proposées
     * @param defaultThreshold seuil de réparations proposé pour un nouveau véhicule (Paramètres › Flotte), ou null
     * @param documentsPanel   documents du véhicule et de ses chauffeurs (fiche existante), ou null
     */
    VehicleEditor(Forms forms, Messages messages, Supplier<LocalDate> today, List<VehicleCategory> categories,
                  Money defaultThreshold, Vehicle existing, List<DocumentType> documentTypes,
                  Node documentsPanel) {
        this.messages = messages;
        this.documentsPanel = documentsPanel;
        this.existing = existing;
        this.categories = categories;
        this.defaultThreshold = defaultThreshold;
        this.form = forms.create();

        form.section(messages.get("vehicle.section.identification"));
        registration = form.text("registration", messages.get("vehicle.registration"))
                .required()
                .maxLength(VehicleRules.REGISTRATION_MAX)
                .onBlur(VehicleRules::normalizeRegistration)
                .rule(Rules.pattern(REGISTRATION, "vehicle.registration.invalidChars"))
                .prompt(messages.get("vehicle.registration.prompt"));
        category = form.choice("categoryId", messages.get("vehicle.category"),
                Choices.activePlus(categories, VehicleCategory::active, VehicleCategory::id,
                        existing == null ? null : existing.categoryId()),
                VehicleCategory::name).required();
        make = form.text("make", messages.get("vehicle.make")).required().maxLength(VehicleRules.MAKE_MAX);
        model = form.text("model", messages.get("vehicle.model")).required().maxLength(VehicleRules.MODEL_MAX);
        status = form.choice("status", messages.get("vehicle.status"), Vehicle.VehicleStatus.class).required();

        form.section(messages.get("vehicle.section.acquisition"));
        acquisitionDate = form.date("acquisitionDate", messages.get("vehicle.acquisitionDate"))
                .rule(Rules.notAfter(today));
        acquisitionMethod = form.choice("acquisitionMethod", messages.get("vehicle.acquisitionMethod"),
                AcquisitionMethod.class).allowEmpty();
        purchasePrice = form.money("purchasePrice", messages.get("vehicle.purchasePrice"));

        form.section(messages.get("vehicle.section.follow"));
        currentMileage = form.number("currentMileage", messages.get("vehicle.currentMileage"))
                .unit(messages.get("unit.km"));
        repairThreshold = form.money("repairThreshold", messages.get("vehicle.repairThreshold"))
                .help(messages.get("vehicle.repairThreshold.help"));
        notes = form.textArea("notes", messages.get("vehicle.notes")).maxLength(VehicleRules.NOTES_MAX);
        drafts = existing == null
                ? DocumentDrafts.addTo(form, messages, DocumentType.DocumentSubject.VEHICLE, documentTypes) : null;

        load(existing);
    }

    private void load(Vehicle v) {
        if (v == null) {
            status.setValue(Vehicle.VehicleStatus.ACTIVE);
            categories.stream().filter(VehicleCategory::active).findFirst().ifPresent(category::setValue);
            repairThreshold.setValue(defaultThreshold);
            return;
        }
        registration.setValue(v.registration());
        category.setValue(Choices.byId(categories, VehicleCategory::id, v.categoryId()));
        make.setValue(v.make());
        model.setValue(v.model());
        status.setValue(v.status());
        acquisitionDate.setValue(v.acquisitionDate());
        acquisitionMethod.setValue(v.acquisitionMethod());
        purchasePrice.setValue(v.purchasePrice());
        currentMileage.setValue(v.currentMileage());
        repairThreshold.setValue(v.repairThreshold());
        notes.setValue(v.notes());
    }

    /** Documents saisis à la création (vide en modification). */
    List<DocumentDraft> documentDrafts() {
        return drafts == null ? List.of() : drafts.drafts();
    }

    @Override
    public String title() {
        return existing == null
                ? messages.get("vehicle.create")
                : messages.get("vehicle.edit", existing.registration());
    }

    @Override
    public String subtitle() {
        return existing == null ? null : existing.make() + " " + existing.model();
    }

    @Override
    public Node header() {
        return documentsPanel;
    }

    @Override
    public Form form() {
        return form;
    }

    @Override
    public Vehicle toEntity() {
        return new Vehicle(
                existing == null ? null : existing.id(),
                existing == null ? 0 : existing.version(),
                registration.getValue(),
                make.getValue(),
                model.getValue(),
                category.getValue() == null ? null : category.getValue().id(),
                status.getValue(),
                acquisitionDate.getValue(),
                acquisitionMethod.getValue(),
                purchasePrice.getValue(),
                currentMileage.getValue(),
                repairThreshold.getValue(),
                notes.getValue());
    }
}
