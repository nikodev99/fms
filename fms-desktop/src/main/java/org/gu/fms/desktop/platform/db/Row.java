package org.gu.fms.desktop.platform.db;

import org.gu.fms.domain.money.CurrencyUnit;
import org.gu.fms.domain.money.Money;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Ligne de résultat avec lecteurs typés.
 * Conventions de stockage : dates en texte ISO-8601, montants en entiers (unités mineures),
 * énumérations par leur nom.
 */
public class Row {
    private final ResultSet rs;

    Row(ResultSet rs) {
        this.rs = rs;
    }

    public String string(String column) throws SQLException {
        return rs.getString(column);
    }

    public Long longOrNull(String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public long longValue(String column) throws SQLException {
        long value = rs.getLong(column);
        if (rs.wasNull()) {
            throw new SQLException("Valeur nulle inattendue dans la colonne " + column);
        }
        return value;
    }

    public int intValue(String column) throws SQLException {
        return Math.toIntExact(rs.getLong(column));
    }

    public boolean bool(String column) throws SQLException {
        return longValue(column) != 0;
    }

    public LocalDate localDate(String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : LocalDate.parse(value);
    }

    public Instant instant(String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Instant.parse(value);
    }

    public <E extends Enum<E>> E enumValue(String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        }catch(IllegalArgumentException e) {
            throw new SQLException("Valeur inconnue '" + value + "' pour " + type.getSimpleName()
                    + " dans la colonne " + column, e);
        }
    }

    public Money money(String column, CurrencyUnit currency) throws SQLException {
        Long minor = longOrNull(column);
        return minor == null ? null : Money.of(minor, currency);
    }
}
