package io.quarkiverse.jimmer.runtime.cfg.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.runtime.ConnectionManager;

@Deprecated
public class QuarkusConnectionManager implements ConnectionManager {

    private final DataSource dataSource;

    public QuarkusConnectionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <R> R execute(Function<Connection, R> block) {
        try (Connection con = dataSource.getConnection()) {
            return block.apply(con);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
