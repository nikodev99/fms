package org.gu.fms.desktop.features.vehicle;

import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.vehicle.Vehicle;
import org.gu.fms.domain.vehicle.VehicleCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VehicleCategoryServiceTest {

    @TempDir
    Path dir;
    private Fixture f;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixture(dir);
    }

    @AfterEach
    void tearDown() throws Exception {
        f.close();
    }

    @Test
    void sixSystemCategoriesAreSeededAndUserCanAddOne() {
        assertEquals(6, f.categories.findAll().size());
        assertTrue(f.categories.findAll().stream().allMatch(VehicleCategory::system));
        VehicleCategory moto = f.categories.save(new VehicleCategory(null, 0, "  Moto   taxi ", 60, true, true));
        assertEquals("Moto taxi", moto.name());
        assertFalse(f.categoryRepo.findById(moto.id()).orElseThrow().system(), "seules les migrations créent des catégories système");
        ValidationException dup = assertThrows(ValidationException.class,
                () -> f.categories.save(new VehicleCategory(null, 0, "moto TAXI", 0, true, false)));
        assertEquals("category.name.taken", dup.errors().get(0).messageKey());
    }

    @Test
    void inactiveCategoryIsRefusedForNewVehiclesButKeptByExistingOnes() {
        Vehicle existing = f.vehicle("BZ 1");
        VehicleCategory taxi = f.categoryRepo.findById(1L).orElseThrow();
        f.categories.save(new VehicleCategory(taxi.id(), taxi.version(), taxi.name(), taxi.sortOrder(), false, true));

        Vehicle kept = f.vehicles.save(existing.withStatus(Vehicle.VehicleStatus.UNDER_MAINTENANCE));
        assertEquals(1L, kept.categoryId());
        ValidationException e = assertThrows(ValidationException.class, () -> f.vehicle("BZ 2"));
        assertEquals("vehicle.category.inactive", e.errors().get(0).messageKey());
        assertEquals(1, f.vehicleRepo.count());
    }
}