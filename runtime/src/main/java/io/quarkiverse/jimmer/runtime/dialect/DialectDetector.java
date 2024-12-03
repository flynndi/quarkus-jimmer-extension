package io.quarkiverse.jimmer.runtime.dialect;

import java.sql.Connection;

import org.babyfish.jimmer.sql.dialect.Dialect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DialectDetector {

    @Nullable
    Dialect detectDialect(@NotNull Connection con);
}
