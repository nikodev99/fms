package org.gu.fms.desktop.features.collection;

import org.gu.fms.domain.collection.DriverBalance;
import org.gu.fms.domain.driver.Driver;

/** Ligne de l'écran Comptes chauffeurs : un chauffeur et ses soldes. */
public record DriverAccountRow(Driver driver, DriverBalance balance) {
}