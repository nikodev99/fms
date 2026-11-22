package org.gu.fms.domain.finance;

/** Sens d'une transaction : on choisit d'abord le sens, puis une catégorie de ce sens (instructions §4.4). */
public enum TransactionDirection {
    INFLOW(1),
    OUTFLOW(-1);

    private final int sign;

    TransactionDirection(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }

    public TransactionDirection opposite() {
        return this == INFLOW ? OUTFLOW : INFLOW;
    }
}
