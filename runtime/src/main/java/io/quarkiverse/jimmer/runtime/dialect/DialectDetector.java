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

        private static final Logger LOGGER = Logger.getLogger(DialectDetector.class);

        public Impl(DataSource dataSource) {
            // Kept for source compatibility; the connection manager owns the supplied connection.
        }

        @Override
        public @Nullable Dialect detectDialect(@NotNull Connection con) {
            try {
                DatabaseMetaData metaData = con.getMetaData();
                return DialectResolver.fromProductName(metaData.getDatabaseProductName());
            } catch (SQLException e) {
                LOGGER.warn("Failed to autodetect jimmer dialect", e);
                return null;
            }
        }
    }
}
