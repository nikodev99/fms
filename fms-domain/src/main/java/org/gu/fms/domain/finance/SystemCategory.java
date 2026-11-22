package org.gu.fms.domain.finance;

/**
 * Catégories créées par la migration et utilisées par les modules. Le code est la seule référence stable
 * (le libellé peut être renommé par l'utilisateur).
 */
public enum SystemCategory {
    COLLECTION("COLLECTION", TransactionDirection.INFLOW, TransactionNature.OPERATING),
    TRANSFER_OUT("TRANSFER_OUT", TransactionDirection.OUTFLOW, TransactionNature.TRANSFER),
    TRANSFER_IN("TRANSFER_IN", TransactionDirection.INFLOW, TransactionNature.TRANSFER),
    CASH_SURPLUS("CASH_SURPLUS", TransactionDirection.INFLOW, TransactionNature.OPERATING),
    CASH_SHORTAGE("CASH_SHORTAGE", TransactionDirection.OUTFLOW, TransactionNature.OPERATING),
    BANK_FEES("BANK_FEES", TransactionDirection.OUTFLOW, TransactionNature.OPERATING),
    MAINTENANCE("MAINTENANCE", TransactionDirection.OUTFLOW, TransactionNature.OPERATING),
    NET_SALARY("NET_SALARY", TransactionDirection.OUTFLOW, TransactionNature.PAYROLL),
    SALARY_ADVANCE("SALARY_ADVANCE", TransactionDirection.OUTFLOW, TransactionNature.DRIVER_ACCOUNT),
    ADVANCE_REPAYMENT("ADVANCE_REPAYMENT", TransactionDirection.INFLOW, TransactionNature.DRIVER_ACCOUNT);

    private final String code;
    private final TransactionDirection direction;
    private final TransactionNature nature;

    SystemCategory(String code, TransactionDirection direction, TransactionNature nature) {
        this.code = code;
        this.direction = direction;
        this.nature = nature;
    }

    public String code() {
        return code;
    }

    public TransactionDirection direction() {
        return direction;
    }

    public TransactionNature nature() {
        return nature;
    }
}
