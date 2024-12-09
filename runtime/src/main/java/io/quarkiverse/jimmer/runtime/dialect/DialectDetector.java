package io.quarkiverse.jimmer.runtime.dialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.dialect.*;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DialectDetector {

    @Nullable
    Dialect detectDialect(@NotNull Connection con);

    class Impl implements DialectDetector {

        private static final Logger LOGGER = Logger.getLogger(DialectDetector.class);

        private final DataSource dataSource;

        public Impl(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public @Nullable Dialect detectDialect(@NotNull Connection con) {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String databaseProductName = metaData.getDatabaseProductName();
                switch (databaseProductName) {
                    case "PostgreSQL":
                        return new PostgresDialect();
                    case "Oracle":
                        return new OracleDialect();
                    case "MySQL":
                        return new MySqlDialect();
                    case "Microsoft SQL Server":
                        return new SqlServerDialect();
                    case "H2":
                        return new H2Dialect();
                    case "SQLite":
                        return new SQLiteDialect();
                    default:
                        return null;
                }
            } catch (SQLException e) {
                LOGGER.warn("Failed to autodetect jimmer dialect", e);
                return null;
            }
        }
    }
}
