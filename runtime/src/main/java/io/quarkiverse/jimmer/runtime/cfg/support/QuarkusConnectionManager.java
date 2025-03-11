package io.quarkiverse.jimmer.runtime.cfg.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.babyfish.jimmer.sql.transaction.Propagation;
import org.babyfish.jimmer.sql.transaction.TxConnectionManager;
import org.jetbrains.annotations.Nullable;

public class QuarkusConnectionManager implements ConnectionManager, TxConnectionManager {

    private final DataSource dataSource;

    private final Supplier<TransactionManager> transactionManagerResolver;

    private volatile Object transactionManagerOrException;

    public QuarkusConnectionManager(DataSource dataSource) {
        this.dataSource = dataSource;
        this.transactionManagerResolver = null;
    }

    public QuarkusConnectionManager(DataSource dataSource, Supplier<TransactionManager> transactionManagerResolver) {
        this.dataSource = dataSource;
        this.transactionManagerResolver = transactionManagerResolver;
    }

    @Override
    public final <R> R execute(Function<Connection, R> block) {
        return execute(null, block);
    }

    @Override
    public <R> R execute(@Nullable Connection con, Function<Connection, R> block) {
        if (null != con) {
            return block.apply(con);
        }
        try (Connection newConnection = dataSource.getConnection()) {
            return block.apply(newConnection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <R> R executeTransaction(Propagation propagation, Function<Connection, R> block) {
        throw new UnsupportedOperationException("Quarkus does not support Jimmer's transaction management");
    }
}
