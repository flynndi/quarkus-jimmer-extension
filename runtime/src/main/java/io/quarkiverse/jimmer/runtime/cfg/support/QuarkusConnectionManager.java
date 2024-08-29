package io.quarkiverse.jimmer.runtime.cfg.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.jetbrains.annotations.Nullable;

public class QuarkusConnectionManager implements ConnectionManager {

    private final DataSource dataSource;

    public QuarkusConnectionManager(DataSource dataSource) {
        this.dataSource = dataSource;
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
}
