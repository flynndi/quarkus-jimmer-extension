package io.quarkiverse.jimmer.runtime.cfg.support;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.jetbrains.annotations.NotNull;

public interface DataSourceAwareConnectionManager extends ConnectionManager {

    @NotNull
    DataSource getDataSource();
}
