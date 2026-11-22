package org.gu.fms.domain.company;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.TextRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Identité de l'entreprise, reprise sur les documents générés (instructions §6.6).
 *
 * @param rccm numéro du Registre du commerce et du crédit mobilier
 * @param niu  numéro d'identification unique (fiscal)
 * @param logoFile nom du fichier logo copié dans le dossier de données (null = logo FMS)
 */
public record CompanyProfile(String name, String legalForm, String rccm, String niu, String address, String city,
                             String phone, String email, String logoFile) {

    public static final CompanyProfile EMPTY = new CompanyProfile(null, null, null, null, null, null, null, null, null);
    public static final int NAME_MAX = 100;
    public static final int SHORT_MAX = 40;
    public static final int ADDRESS_MAX = 200;
    public static final int EMAIL_MAX = 100;

    public CompanyProfile normalize() {
        return new CompanyProfile(TextRules.normalizeSpaces(name), TextRules.normalizeSpaces(legalForm),
                TextRules.normalizeSpaces(rccm), TextRules.normalizeSpaces(niu), TextRules.normalize(address),
                TextRules.normalizeSpaces(city), TextRules.normalizeSpaces(phone), TextRules.normalize(email), logoFile);
    }

    public List<FieldError> validate() {
        List<FieldError> errors = new ArrayList<>();
        TextRules.requireText(errors, "name", name, NAME_MAX);
        TextRules.maxLength(errors, "legalForm", legalForm, SHORT_MAX);
        TextRules.maxLength(errors, "rccm", rccm, SHORT_MAX);
        TextRules.maxLength(errors, "niu", niu, SHORT_MAX);
        TextRules.maxLength(errors, "address", address, ADDRESS_MAX);
        TextRules.maxLength(errors, "city", city, SHORT_MAX);
        TextRules.phone(errors, "phone", phone);
        TextRules.maxLength(errors, "email", email, EMAIL_MAX);
        TextRules.email(errors, "email", email);
        return errors;
    }

    public CompanyProfile withLogo(String file) {
        return new CompanyProfile(name, legalForm, rccm, niu, address, city, phone, email, file);
    }
}
