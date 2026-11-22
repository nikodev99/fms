package org.gu.fms.domain.driver;

import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Chauffeur (instructions §4.2) et accord de rémunération (§4.7).
 * <p>
 * Ne contient que l'ACCORD : montants convenus, jamais de soldes. Les avances en cours, manquants cumulés,
 * dettes et caution effectivement versée seront CALCULÉS par les modules Versements et Paie à partir
 * de leurs mouvements ; ils ne sont jamais saisis ici.
 *
 * @param dailyTarget   versement journalier attendu (base du calcul des manquants)
 * @param depositAgreed caution convenue à l'embauche (montant à constituer, pas montant reçu)
 * @param cnssSubject   le chauffeur est-il déclaré à la CNSS ? (cotisations CNSS appliquées en paie)
 */
public record Driver(
        Long id,
        long version,
        String lastName,
        String firstName,
        String phone,
        String address,
        String idNumber,
        String licenceNumber,
        LocalDate licenceExpiry,
        LocalDate hireDate,
        DriverStatus status,
        PayScheme payScheme,
        Money fixedSalary,
        Percent commissionRate,
        Money dailyTarget,
        Money depositAgreed,
        boolean cnssSubject,
        String cnssNumber,
        boolean itsSubject,
        MaritalStatus maritalStatus,
        Integer dependentChildren,
        String notes) {

    public Driver {
        Objects.requireNonNull(status, "status");
    }

    public boolean isNew() {
        return id == null;
    }

    /** « MBEMBA Jean ». */
    public String displayName() {
        if (lastName == null) {
            return firstName == null ? "" : firstName;
        }
        return firstName == null ? lastName : lastName + " " + firstName;
    }

    public Driver withIdentity(long newId, long newVersion) {
        return new Driver(newId, newVersion, lastName, firstName, phone, address, idNumber, licenceNumber,
                licenceExpiry, hireDate, status, payScheme, fixedSalary, commissionRate, dailyTarget, depositAgreed,
                cnssSubject, cnssNumber, itsSubject, maritalStatus, dependentChildren, notes);
    }

    public Driver withStatus(DriverStatus newStatus) {
        return new Driver(id, version, lastName, firstName, phone, address, idNumber, licenceNumber,
                licenceExpiry, hireDate, newStatus, payScheme, fixedSalary, commissionRate, dailyTarget, depositAgreed,
                cnssSubject, cnssNumber, itsSubject, maritalStatus, dependentChildren, notes);
    }

    /** Copie normalisée (voir {@link DriverRules#normalize(Driver)}). */
    Driver normalized(String nLast, String nFirst, String nPhone, String nAddress, String nId, String nLicence,
                      Money nFixed, Percent nRate, String nCnss, String nNotes) {
        return new Driver(id, version, nLast, nFirst, nPhone, nAddress, nId, nLicence, licenceExpiry, hireDate,
                status, payScheme, nFixed, nRate, dailyTarget, depositAgreed, cnssSubject, nCnss, itsSubject, maritalStatus,
                dependentChildren, nNotes);
    }

    /** Situation d'emploi d'un chauffeur (instructions §4.2). Un chauffeur n'est jamais supprimé. */
    public enum DriverStatus {
        ACTIVE,
        SUSPENDED,
        LEFT;

        /** Peut-il recevoir une nouvelle affectation ? */
        public boolean canBeAssigned() {
            return this == ACTIVE;
        }
    }
}
