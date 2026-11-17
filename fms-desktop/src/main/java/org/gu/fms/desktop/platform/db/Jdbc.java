package org.gu.fms.desktop.platform.db;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.gu.fms.domain.money.Money;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Exécution SQL paramétrée. Toujours des paramètres liés (jamais de concaténation de valeurs).
 * <p>
 * Types de paramètres acceptés : null, String, Long, Integer, Boolean, LocalDate, Instant, Enum, Money.
 */
@Singleton
public final class Jdbc {
    private final TransactionManager tx;

    @Inject
    public Jdbc(TransactionManager tx) {
        this.tx = tx;
    }

    public <T>List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        return tx.withConnection(c -> {
            try(PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
                Row row = new Row(rs);
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(row));
                }
                return result;
            }
        });
    }

    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        if (rows.size() > 1) {
            throw new IllegalStateException("Une ligne attendue, " + rows.size() + " obtenues : " + sql);
        }
        return rows.stream().findFirst();
    }

    public long queryLong(String sql, Object... params) {
        return tx.withConnection(c -> {
            try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DataAccessException("Aucun résultat : " + sql);
                }
                long value = rs.getLong(1);
                if (rs.wasNull()) {
                    throw new DataAccessException("Résultat nul : " + sql);
                }
                return value;
            }
        });
    }

    public boolean exists(String sql, Object... params) {
        return tx.withConnection(c -> {
            try (PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        });
    }

    public int update(String sql, Object... params) {
        return tx.withConnection(c -> {
            try(PreparedStatement ps = prepare(c, sql, params)) {
                return ps.executeUpdate();
            }
        });
    }

    public long insert(String sql, Object... params) {
        return tx.withConnection(c -> {
            try(PreparedStatement ps = prepare(c, sql, params)) {
                ps.executeUpdate();
            }
            try(Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    private PreparedStatement prepare(Connection c, String sql, Object[] params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        try {
            for(int i = 0; i < params.length; i++) {
                bind(ps, i + 1, params[i]);
            }
            return ps;
        }catch (SQLException | RuntimeException e) {
            ps.close();
            throw e;
        }
    }

    static void bind(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NULL);
        } else if (value instanceof String s) {
            ps.setString(index, s);
        } else if (value instanceof Long l) {
            ps.setLong(index, l);
        } else if (value instanceof Integer i) {
            ps.setLong(index, i);
        } else if (value instanceof Boolean b) {
            ps.setLong(index, b ? 1 : 0);
        } else if (value instanceof LocalDate d) {
            ps.setString(index, d.toString());
        } else if (value instanceof Instant t) {
            ps.setString(index, t.toString());
        } else if (value instanceof Enum<?> e) {
            ps.setString(index, e.name());
        } else if (value instanceof Money m) {
            ps.setLong(index, m.minor());
        } else {
            throw new IllegalArgumentException("Type de paramètre SQL non pris en charge : " + value.getClass().getName());
        }
    }
}
