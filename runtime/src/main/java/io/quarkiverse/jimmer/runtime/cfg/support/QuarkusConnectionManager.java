package io.quarkiverse.jimmer.runtime.cfg.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.transaction.Propagation;
import org.babyfish.jimmer.sql.transaction.TxConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.runtime.transaction.QuarkusTransactionExecutor;
import io.quarkus.arc.Arc;

public class QuarkusConnectionManager implements DataSourceAwareConnectionManager, TxConnectionManager {

    private final DataSource dataSource;

    public QuarkusConnectionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @NotNull
    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public final <R> R execute(Function<Connection, R> block) {
        return execute(null, block);
    }

    @Override
    public final <R> R execute(@Nullable Connection con, Function<Connection, R> block) {
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
        // Acquire the JDBC connection inside the intercepted boundary, after Narayana applies propagation.
        return Arc.container().instance(QuarkusTransactionExecutor.class).get()
                .execute(propagation, () -> execute(block));
    }
}
