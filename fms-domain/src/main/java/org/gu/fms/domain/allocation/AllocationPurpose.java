package org.gu.fms.domain.allocation;

/** Poste auquel une provision est destinée (instructions §4.6). */
public enum AllocationPurpose {
    MAINTENANCE,
    MAJOR_REPAIRS,
    INSURANCE_DOCUMENTS,
    DRIVER_PAY,
    PAYROLL_CHARGES,
    TAXES,
    LOAN_REPAYMENT,
    RESERVE,
    OWNER_INCOME,
    OTHER
}
