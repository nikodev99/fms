package org.gu.fms.domain.driver;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.common.Percent;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DriverRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final Money FIXED = Money.of(120_000, CurrencyUnit.XAF);

    static Driver driver(PayScheme scheme, Money fixed, Percent rate) {
        return new Driver(null, 0, " mbemba ", " Jean  Paul ", "06 474 50 67", null, null, " cg-123 ",
                TODAY.plusMonths(3), TODAY.minusYears(1), Driver.DriverStatus.ACTIVE, scheme, fixed, rate,
                Money.of(25_000, CurrencyUnit.XAF), Money.of(100_000, CurrencyUnit.XAF), false, "999",
                MaritalStatus.MARRIED, 2, null);
    }

    private static List<String> codes(List<FieldError> errors) {
        return errors.stream().map(e -> e.field() + ":" + e.messageKey()).collect(Collectors.toList());
    }

    @Test
    void normalizesNamesLicenceAndClearsIrrelevantValues() {
        Driver d = DriverRules.normalize(driver(PayScheme.PERCENTAGE, FIXED, Percent.of("15")));
        assertEquals("MBEMBA", d.lastName());
        assertEquals("Jean Paul", d.firstName());
        assertEquals("CG-123", d.licenceNumber());
        assertNull(d.fixedSalary(), "un accord au pourcentage ne garde pas de fixe");
        assertNull(d.cnssNumber(), "numéro CNSS effacé si le chauffeur n'est pas déclaré");
        assertTrue(DriverRules.validate(d, TODAY).isEmpty());
        assertEquals("MBEMBA Jean Paul", d.displayName());
    }

    @Test
    void payAgreementMustMatchScheme() {
        Driver fixedMissing = DriverRules.normalize(driver(PayScheme.FIXED_PLUS_COMMISSION, null, Percent.ZERO));
        assertEquals(List.of("fixedSalary:validation.required", "commissionRate:validation.positive"),
                codes(DriverRules.validate(fixedMissing, TODAY)));
        Driver noScheme = DriverRules.normalize(driver(null, FIXED, null));
        assertEquals(List.of("payScheme:validation.required"), codes(DriverRules.validate(noScheme, TODAY)));
    }

    @Test
    void requiredFieldsPhoneAndDates() {
        Driver d = new Driver(null, 0, " ", null, "abc", null, null, null, null, TODAY.plusDays(1),
                Driver.DriverStatus.ACTIVE, PayScheme.FIXED, FIXED, null, null, null, false, null, null, 31, null);
        assertEquals(List.of("lastName:validation.required", "firstName:validation.required", "phone:validation.phone",
                        "hireDate:validation.notInFuture", "dependentChildren:validation.range"),
                codes(DriverRules.validate(DriverRules.normalize(d), TODAY)));
    }

    @Test
    void expiredLicenceIsAnAlertNotAnError() {
        Driver d = DriverRules.normalize(driver(PayScheme.FIXED, FIXED, null));
        Driver expired = new Driver(null, 0, d.lastName(), d.firstName(), d.phone(), null, null, null,
                TODAY.minusDays(1), null, Driver.DriverStatus.ACTIVE, PayScheme.FIXED, FIXED, null, null, null, false, null,
                null, null, null);
        assertTrue(DriverRules.validate(expired, TODAY).isEmpty());
        assertTrue(DriverRules.licenceExpired(expired, TODAY));
        assertFalse(DriverRules.licenceExpiresWithin(expired, TODAY, 30));
        assertTrue(DriverRules.licenceExpiresWithin(d, TODAY, 92));
        assertFalse(DriverRules.licenceExpiresWithin(d, TODAY, 30));
    }
}
