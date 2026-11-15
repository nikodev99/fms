package org.gu.fms.desktop.platform.db;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Accès sérialisé à la connexion et gestion des transactions.
 *
 * <pre>{@code
 * tx.inTransaction(c -> {
 *     long id = repository.insert(vehicle);
 *     audit.created("vehicle", id, snapshot);   // même transaction : tout ou rien
 *     return id;
 * });
 * }</pre>
 *
 * Une transaction imbriquée rejoint la transaction en cours. Toute exception (SQL ou métier)
 * annule l'ensemble de la transaction la plus externe.
 */
@Singleton
public class TransactionManager {
    private final Database database;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

    @Inject
    public TransactionManager(Database database) {
        this.database = database;
    }

    public <T> T inTransaction(SqlWork<T> work) {
        lock.lock();
        try {
            Connection connection = database.connection();
            int current = depth.get();
            if (current > 0) {
                depth.set(current + 1);
                try {
                    return work.run(connection);
                }catch (SQLException e) {
                    throw DataAccessException.wrap(e);
                }finally {
                    depth.set(current);
                }
            }
            return runOutermost(connection, work);
        }finally {
            lock.unlock();
        }
    }

    /** Variante sans résultat de {@link #inTransaction(SqlWork)}. */
    public void runInTransaction(SqlRunnable work) {
        inTransaction(c -> {
            work.run(c);
            return null;
        });
    }

    /** Lecture (ou écriture unitaire) : rejoint la transaction en cours s'il y en a une. */
    public <T> T withConnection(SqlWork<T> work) {
        lock.lock();
        try {
            return work.run(database.connection());
        } catch (SQLException e) {
            throw DataAccessException.wrap(e);
        } finally {
            lock.unlock();
        }
    }

    public boolean isInTransaction() {
        return depth.get() > 0;
    }

    private <T> T runOutermost(Connection connection, SqlWork<T> work) {
        boolean committed = false;
        try {
            connection.setAutoCommit(false);
            depth.set(1);
            T result = work.run(connection);
            connection.commit();
            committed = true;
            return result;
        }catch (SQLException e) {
            throw DataAccessException.wrap(e);
        }finally {
            depth.set(0);
            try {
                if (!committed) connection.rollback();
                connection.setAutoCommit(true);
            }catch (SQLException e) {
                // La connexion est dans un état incertain : on le signale sans masquer l'erreur d'origine.
                System.getLogger(TransactionManager.class.getName())
                        .log(System.Logger.Level.ERROR, "Annulation de transaction impossible", e);
            }
        }
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void run(Connection connection) throws SQLException;
    }
}
