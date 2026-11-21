package org.gu.fms.desktop.features.maintenance;

import org.gu.fms.domain.maintenance.RepairAssessment;
import org.gu.fms.domain.vehicle.Vehicle;

/** Ligne de l'écran « Seuils de réparation » : un véhicule et son cumul de réparations. */
public record RepairRow(Vehicle vehicle, RepairAssessment assessment, long repairCount) {
}
