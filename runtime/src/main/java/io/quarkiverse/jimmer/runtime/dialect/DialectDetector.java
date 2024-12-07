package io.quarkiverse.jimmer.runtime.dialect;

import java.sql.Connection;

import org.babyfish.jimmer.sql.dialect.Dialect;
import org.jboss.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DialectDetector {

    @Nullable
    Dialect detectDialect(@NotNull Connection con);

    class Impl implements DialectDetector {

        private static final Logger log = Logger.getLogger(DialectDetector.class);

        @Override
        public @Nullable Dialect detectDialect(@NotNull Connection con) {
            return null;
        }
    }
}
