package io.quarkiverse.jimmer.runtime.cfg;

import java.util.*;

import org.babyfish.jimmer.sql.runtime.DatabaseValidationMode;

import io.quarkus.datasource.common.runtime.DataSourceUtil;
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
     * jimmer.transactionCacheOperatorFixedDelay
     */
    @WithDefault("5s")
    String transactionCacheOperatorFixedDelay();

    /**
     * jimmer.databaseValidationMode
     */
    @WithDefault("NONE")
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
