package org.gu.fms.desktop.features.driver;

import javafx.beans.binding.Bindings;
import org.gu.fms.desktop.features.document.DocumentDraft;
import org.gu.fms.desktop.features.document.DocumentDrafts;
import org.gu.fms.desktop.platform.i18n.Messages;
import org.gu.fms.desktop.platform.ui.crud.EntityEditor;
import org.gu.fms.desktop.platform.ui.form.*;
import org.gu.fms.domain.common.TextRules;
import org.gu.fms.domain.document.DocumentType;
import org.gu.fms.domain.driver.Driver;
import org.gu.fms.domain.driver.Driver.DriverStatus;
import org.gu.fms.domain.driver.DriverRules;
import org.gu.fms.domain.driver.MaritalStatus;
import org.gu.fms.domain.driver.PayScheme;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fiche chauffeur. Seuls les champs qui correspondent au mode de rémunération choisi sont affichés.
 * Aucun solde ici : avances, manquants, dettes et caution versée viendront des modules Versements et Paie.
 */
final class DriverEditor implements EntityEditor<Driver> {

    private final Messages messages;
    private final Driver existing;
    private final Form form;
    private final TextInput lastName;
    private final TextInput firstName;
    private final TextInput phone;
    private final TextAreaInput address;
    private final TextInput idNumber;
    private final DocumentDrafts drafts;
    private final DateInput hireDate;
    private final ChoiceInput<DriverStatus> status;
    private final ChoiceInput<PayScheme> payScheme;
    private final MoneyInput fixedSalary;
    private final PercentInput commissionRate;
    private final MoneyInput dailyTarget;
    private final MoneyInput depositAgreed;
    private final CheckInput cnssSubject;
    private final TextInput cnssNumber;
    private final CheckInput itsSubject;
    private final ChoiceInput<MaritalStatus> maritalStatus;
    private final NumberInput children;
    private final TextAreaInput notes;

    DriverEditor(Forms forms, Messages messages, Supplier<LocalDate> today, Driver existing,
                 List<DocumentType> documentTypes) {
        this.messages = messages;
        this.existing = existing;
        form = forms.create();

        form.section(messages.get("driver.section.identity"));
        lastName = form.text("lastName", messages.get("driver.lastName")).required().maxLength(DriverRules.NAME_MAX)
                .onBlur(s -> s == null ? null : s.toUpperCase(messages.locale()));
        firstName = form.text("firstName", messages.get("driver.firstName")).required().maxLength(DriverRules.NAME_MAX);
        phone = form.text("phone", messages.get("driver.phone")).required().maxLength(20)
                .rule(Rules.pattern(TextRules.PHONE, "validation.phone")).prompt(messages.get("driver.phone.prompt"))
                .help(messages.get("driver.phone.help"));
        idNumber = form.text("idNumber", messages.get("driver.idNumber")).maxLength(DriverRules.ID_NUMBER_MAX);
        address = form.textArea("address", messages.get("driver.address")).maxLength(DriverRules.ADDRESS_MAX).rows(2);

        form.section(messages.get("driver.section.employment"));
        hireDate = form.date("hireDate", messages.get("driver.hireDate")).rule(Rules.notAfter(today));
        status = form.choice("status", messages.get("driver.status"), DriverStatus.class).required()
                .help(messages.get("driver.status.help"));

        form.section(messages.get("driver.section.pay"));
        payScheme = form.choice("payScheme", messages.get("driver.payScheme"), PayScheme.class).required().span(2);
        var hasFixed = Bindings.createBooleanBinding(
                () -> payScheme.getValue() != null && payScheme.getValue().hasFixedPart(), payScheme.valueProperty());
        var hasCommission = Bindings.createBooleanBinding(
                () -> payScheme.getValue() != null && payScheme.getValue().hasCommission(), payScheme.valueProperty());
        fixedSalary = form.money("fixedSalary", messages.get("driver.fixedSalary")).shownWhen(hasFixed);
        commissionRate = form.percent("commissionRate", messages.get("driver.commissionRate")).shownWhen(hasCommission)
                .help(messages.get("driver.commissionRate.help"));
        dailyTarget = form.money("dailyTarget", messages.get("driver.dailyTarget"))
                .help(messages.get("driver.dailyTarget.help"));
        depositAgreed = form.money("depositAgreed", messages.get("driver.depositAgreed"))
                .help(messages.get("driver.depositAgreed.help"));

        form.section(messages.get("driver.section.social"));
        cnssSubject = form.check("cnssSubject", messages.get("driver.cnssSubject"))
                .help(messages.get("driver.cnssSubject.help"));
        cnssNumber = form.text("cnssNumber", messages.get("driver.cnssNumber")).maxLength(DriverRules.CNSS_MAX)
                .shownWhen(cnssSubject.valueProperty());
        itsSubject = form.check("itsSubject", messages.get("driver.itsSubject"))
                .help(messages.get("driver.itsSubject.help"));
        maritalStatus = form.choice("maritalStatus", messages.get("driver.maritalStatus"), MaritalStatus.class)
                .allowEmpty().help(messages.get("driver.maritalStatus.help"));
        children = form.number("dependentChildren", messages.get("driver.children"))
                .rule(Rules.range(0, DriverRules.CHILDREN_MAX));

        notes = form.textArea("notes", messages.get("driver.notes")).maxLength(DriverRules.NOTES_MAX);
        drafts = existing == null
                ? DocumentDrafts.addTo(form, messages, DocumentType.DocumentSubject.DRIVER, documentTypes) : null;
        load(existing);
    }

    private void load(Driver d) {
        if (d == null) {
            status.setValue(DriverStatus.ACTIVE);
            payScheme.setValue(PayScheme.PERCENTAGE);
            itsSubject.setValue(true);
            return;
        }
        lastName.setValue(d.lastName());
        firstName.setValue(d.firstName());
        phone.setValue(d.phone());
        address.setValue(d.address());
        idNumber.setValue(d.idNumber());
        hireDate.setValue(d.hireDate());
        status.setValue(d.status());
        payScheme.setValue(d.payScheme());
        fixedSalary.setValue(d.fixedSalary());
        commissionRate.setValue(d.commissionRate());
        dailyTarget.setValue(d.dailyTarget());
        depositAgreed.setValue(d.depositAgreed());
        cnssSubject.setValue(d.cnssSubject());
        cnssNumber.setValue(d.cnssNumber());
        itsSubject.setValue(d.itsSubject());
        maritalStatus.setValue(d.maritalStatus());
        children.setValue(d.dependentChildren() == null ? null : (long) d.dependentChildren());
        notes.setValue(d.notes());
    }

    @Override
    public String title() {
        return existing == null ? messages.get("driver.create") : messages.get("driver.edit", existing.displayName());
    }

    @Override
    public String subtitle() {
        return existing == null ? null : existing.phone();
    }

    /** Documents saisis à la création (vide en modification). */
    List<DocumentDraft> documentDrafts() {
        return drafts == null ? List.of() : drafts.drafts();
    }

    @Override
    public Form form() {
        return form;
    }

    @Override
    public Driver toEntity() {
        Long kids = children.getValue();
        return new Driver(existing == null ? null : existing.id(), existing == null ? 0 : existing.version(),
                lastName.getValue(), firstName.getValue(), phone.getValue(), address.getValue(), idNumber.getValue(),
                hireDate.getValue(), status.getValue(),
                payScheme.getValue(), fixedSalary.getValue(), commissionRate.getValue(), dailyTarget.getValue(),
                depositAgreed.getValue(), cnssSubject.getValue(), cnssNumber.getValue(), itsSubject.getValue(),
                maritalStatus.getValue(),
                kids == null ? null : kids.intValue(), notes.getValue());
    }
}