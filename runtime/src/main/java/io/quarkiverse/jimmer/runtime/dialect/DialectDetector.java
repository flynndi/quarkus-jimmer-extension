package io.quarkiverse.jimmer.runtime.dialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.dialect.Dialect;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DialectDetector {

    @Nullable
    Dialect detectDialect(@NotNull Connection con);

    class Impl implements DialectDetector {

        private static final Logger log = Logger.getLogger(DialectDetector.class);

        private final DataSource dataSource;

        public Impl(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public @Nullable Dialect detectDialect(@NotNull Connection con) {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String databaseProductName = metaData.getDatabaseProductName();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }
}
