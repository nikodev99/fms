package org.gu.fms.domain.vehicle;

import org.gu.fms.domain.common.FieldError;
import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
    private static final Long TAXI = 1L;

    private static Vehicle vehicle(String registration, String make, String model) {
        return new Vehicle(null, 0, registration, make, model, TAXI, Vehicle.VehicleStatus.ACTIVE,
                null, null, null, null, null, null);
    }

    private static List<String> codes(List<FieldError> errors) {
        return errors.stream().map(e -> e.field() + ":" + e.messageKey()).collect(Collectors.toList());
    }

    @Test
    void normalizesRegistration() {
        assertEquals("BZ 1234 A", VehicleRules.normalizeRegistration("  bz   1234\ta "));
        assertNull(VehicleRules.normalizeRegistration("   "));
        assertNull(VehicleRules.normalizeRegistration(null));
    }

    @Test
    void validVehicleHasNoErrors() {
        Vehicle v = VehicleRules.normalize(vehicle(" bz 1234 a", " Toyota ", "Corolla"));
        assertTrue(VehicleRules.validate(v, TODAY).isEmpty());
        assertEquals("Toyota", v.make());
    }

    @Test
    void requiredFields() {
        Vehicle v = VehicleRules.normalize(vehicle(" ", "", null));
        assertEquals(List.of("registration:validation.required", "make:validation.required",
                "model:validation.required"), codes(VehicleRules.validate(v, TODAY)));
    }

    @Test
    void categoryIsRequired() {
        Vehicle v = new Vehicle(null, 0, "BZ 1", "Toyota", "Corolla", 1L, Vehicle.VehicleStatus.ACTIVE,
                null, null, null, null, null, null);
        assertEquals(List.of("categoryId:validation.required"), codes(VehicleRules.validate(v, TODAY)));
    }

    @Test
    void registrationCharactersAndLength() {
        Vehicle v = VehicleRules.normalize(vehicle("BZ_1234/A", "Toyota", "Corolla"));
        assertEquals(List.of("registration:vehicle.registration.invalidChars"), codes(VehicleRules.validate(v, TODAY)));

        Vehicle tooLong = VehicleRules.normalize(vehicle("A".repeat(21), "Toyota", "Corolla"));
        FieldError error = VehicleRules.validate(tooLong, TODAY).get(0);
        assertEquals("validation.maxLength", error.messageKey());
        assertEquals(List.of(20), error.args());
    }

    @Test
    void amountsMileageAndDate() {
        Money negative = Money.of(-1, CurrencyUnit.XAF);
        Vehicle v = new Vehicle(null, 0, "BZ 1", "Toyota", "Corolla", TAXI, Vehicle.VehicleStatus.ACTIVE,
                TODAY.plusDays(1), Vehicle.AcquisitionMethod.CASH, negative, -5L, negative, null);
        assertEquals(List.of("acquisitionDate:validation.notInFuture", "purchasePrice:validation.notNegative",
                        "repairThreshold:validation.notNegative", "currentMileage:validation.notNegative"),
                codes(VehicleRules.validate(v, TODAY)));
    }

    @Test
    void acquisitionTodayIsAccepted() {
        Vehicle v = new Vehicle(null, 0, "BZ 1", "Toyota", "Corolla", TAXI, Vehicle.VehicleStatus.ACTIVE,
                TODAY, Vehicle.AcquisitionMethod.LOAN, Money.of(8_000_000, CurrencyUnit.XAF), 0L, null, null);
        assertTrue(VehicleRules.validate(v, TODAY).isEmpty());
    }
}
