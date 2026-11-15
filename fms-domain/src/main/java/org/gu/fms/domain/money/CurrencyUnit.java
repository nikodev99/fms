package org.gu.fms.domain.money;

import java.util.Objects;
import java.util.regex.Pattern;

public record CurrencyUnit(String code, int fractionDigits) {
    private static final Pattern ISO_4217 = Pattern.compile("[A-Z]{3}");

    public static final CurrencyUnit XAF = new CurrencyUnit("XAF", 0);

    public CurrencyUnit {
        Objects.requireNonNull(code, "code");
        if (!ISO_4217.matcher(code).matches())
            throw new IllegalArgumentException("Code devise ISO 4217 invalide : " + code);
        if (fractionDigits < 0 || fractionDigits > 4)
            throw new IllegalArgumentException("Nombre de décimales invalide : " + fractionDigits);
    }

    @Override
    public String toString() {
        return code;
    }
}
