package io.quarkiverse.jimmer.runtime.cfg;

import java.util.*;

import org.babyfish.jimmer.sql.runtime.DatabaseValidationMode;

import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.*;

@ConfigMapping(prefix = "quarkus.jimmer")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface JimmerRuntimeConfig {

    /**
     * Datasource.
     */
    @ConfigDocMapKey("datasource-name")
    @WithParentName
    @WithDefaults
    @WithUnnamedKey(DataSourceUtil.DEFAULT_DATASOURCE_NAME)
    Map<String, JimmerDataSourceRuntimeConfig> dataSources();

    /**
     * Interval for transaction-cache retry when the application includes Quarkus Scheduler or Quartz.
     * Set to {@code off} or {@code disabled} to disable periodic retry. Successful-commit callbacks still run.
     * Without a scheduler extension this value does not schedule a task.
     */
    @WithDefault("5s")
    String transactionCacheOperatorFixedDelay();

    /**
     * Database validation mode. If absent, the deprecated database-validation.mode setting is used.
     */
    @WithDefault("${quarkus.jimmer.database-validation.mode:NONE}")
    @ConfigDocDefault("NONE")
    DatabaseValidationMode databaseValidationMode();

    /**
     * jimmer.databaseValidation
     */
    @Deprecated
    DatabaseValidation databaseValidation();

    @Deprecated
    @ConfigGroup
    interface DatabaseValidation {

        /**
         * mode
         */
        @WithDefault("NONE")
        DatabaseValidationMode mode();

        /**
         * catalog
         */
        @Deprecated
        Optional<String> catalog();

        /**
         * schema
         */
        @Deprecated
        Optional<String> schema();
    }
}
