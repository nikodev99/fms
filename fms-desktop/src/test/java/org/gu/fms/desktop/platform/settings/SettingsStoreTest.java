package org.gu.fms.desktop.platform.settings;

import org.gu.fms.desktop.features.settings.RegionalService;
import org.gu.fms.desktop.testing.Fixture;
import org.gu.fms.domain.common.ValidationException;
import org.gu.fms.domain.money.CurrencyUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsStoreTest {

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
    void writesAuditsDifferencesAndDeletesNulls() {
        Map<String, String> values = new HashMap<>();
        values.put("company.name", "Global Underscore");
        values.put("company.email", null);
        f.settingsStore.putAll(values, null);
        assertEquals("Global Underscore", f.settingsStore.get("company.name").orElseThrow());
        assertEquals(Map.of("company.name", "Global Underscore"), f.settingsStore.byPrefix("company."));
        String changes = f.db.jdbc.queryOne("SELECT changes FROM audit_log WHERE entity_type = 'setting'",
                r -> r.string("changes")).orElseThrow();
        assertEquals("{\"company.name\":{\"from\":null,\"to\":\"Global Underscore\"}}", changes);
    }

    @Test
    void regionalSettingsLoadFromDatabaseAndCurrencyLocksOnceAmountsExist() {
        AppSettings loaded = AppSettings.load(f.settingsStore);
        assertEquals(CurrencyUnit.XAF, loaded.currency());
        assertEquals(ZoneId.of("Africa/Brazzaville"), loaded.zone());

        RegionalService regional = new RegionalService(f.settingsStore, f.db.jdbc, AppSettings.CONGO);
        assertFalse(regional.currencyLocked());
        assertTrue(regional.save(new RegionalService.Regional("CG", CurrencyUnit.XAF, ZoneId.of("Africa/Douala"))),
                "redémarrage nécessaire");
        f.vehicle("BZ 1");
        assertTrue(regional.currencyLocked());
        ValidationException e = assertThrows(ValidationException.class, () -> regional.save(
                new RegionalService.Regional("CG", new CurrencyUnit("EUR", 2), ZoneId.of("Africa/Douala"))));
        assertEquals("regional.currency.locked", e.errors().get(0).messageKey());
    }
}
