package org.gu.fms.desktop.features.vehicle;

import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.VehicleRules;

import java.time.LocalDate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

final class VehicleEditor implements EntityEditor<Vehicle> {
    private static final Pattern REGISTRATION = Pattern.compile("[A-Za-z0-9 -]+");

    private final Messages messages;
    private final Vehicle existing;
    private final Form form;
    private final TextInput registration;
    private final ChoiceInput<Vehicle.VehicleCategory> category;
    private final TextInput make;
    private final TextInput model;
    private final ChoiceInput<Vehicle.VehicleStatus> status;
    private final DateInput acquisitionDate;
    private final ChoiceInput<Vehicle.AcquisitionMethod> acquisitionMethod;
    private final MoneyInput purchasePrice;
    private final NumberInput currentMileage;
    private final MoneyInput repairThreshold;
    private final TextAreaInput notes;

    VehicleEditor(Forms forms, Messages messages, Supplier<LocalDate> today, Vehicle existing) {
        this.messages = messages;
        this.existing = existing;
        this.form = forms.create();

        form.section(messages.get("vehicle.section.identification"));
        registration = form.text("registration", messages.get("vehicle.registration"))
                .required()
                .maxLength(VehicleRules.REGISTRATION_MAX)
                .onBlur(VehicleRules::normalizeRegistration)
                .rule(Rules.pattern(REGISTRATION, "vehicle.registration.invalidChars"))
                .prompt(messages.get("vehicle.registration.prompt"));
        category = form.choice("category", messages.get("vehicle.category"), Vehicle.VehicleCategory.class).required();
        make = form.text("make", messages.get("vehicle.make")).required().maxLength(VehicleRules.MAKE_MAX);
        model = form.text("model", messages.get("vehicle.model")).required().maxLength(VehicleRules.MODEL_MAX);
        status = form.choice("status", messages.get("vehicle.status"), Vehicle.VehicleStatus.class).required();

        form.section(messages.get("vehicle.section.acquisition"));
        acquisitionDate = form.date("acquisitionDate", messages.get("vehicle.acquisitionDate"))
                .rule(Rules.notAfter(today));
        acquisitionMethod = form.choice("acquisitionMethod", messages.get("vehicle.acquisitionMethod"),
                Vehicle.AcquisitionMethod.class).allowEmpty();
        purchasePrice = form.money("purchasePrice", messages.get("vehicle.purchasePrice"));

        form.section(messages.get("vehicle.section.follow"));
        currentMileage = form.number("currentMileage", messages.get("vehicle.currentMileage"))
                .unit(messages.get("unit.km"));
        repairThreshold = form.money("repairThreshold", messages.get("vehicle.repairThreshold"))
                .help(messages.get("vehicle.repairThreshold.help"));
        notes = form.textArea("notes", messages.get("vehicle.notes")).maxLength(VehicleRules.NOTES_MAX);

        load(existing);
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
                category.getValue(),
                status.getValue(),
                acquisitionDate.getValue(),
                acquisitionMethod.getValue(),
                purchasePrice.getValue(),
                currentMileage.getValue(),
                repairThreshold.getValue(),
                notes.getValue());
    }

    private void load(Vehicle v) {
        if (v == null) {
            status.setValue(Vehicle.VehicleStatus.ACTIVE);
            category.setValue(Vehicle.VehicleCategory.TAXI);
            return;
        }
        registration.setValue(v.registration());
        category.setValue(v.category());
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
}
