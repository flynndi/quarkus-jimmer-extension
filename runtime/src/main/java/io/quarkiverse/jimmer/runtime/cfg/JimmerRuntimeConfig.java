package io.quarkiverse.jimmer.runtime.cfg;

import java.util.*;

import org.babyfish.jimmer.sql.EnumType;
import org.babyfish.jimmer.sql.fetcher.ReferenceFetchType;
import org.babyfish.jimmer.sql.runtime.DatabaseValidationMode;
import org.babyfish.jimmer.sql.runtime.IdOnlyTargetCheckingLevel;

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
     * jimmer.defaultReferenceFetchType
     */
    @WithDefault("SELECT")
    ReferenceFetchType defaultReferenceFetchType();

    /**
     * jimmer.maxJoinFetchDepth
     */
    @WithDefault("3")
    OptionalInt maxJoinFetchDepth();

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

    /**
     * jimmer.defaultDissociationActionCheckable
     */
    @WithDefault("true")
    boolean defaultDissociationActionCheckable();

    /**
     * jimmer.idOnlyTargetCheckingLevel
     */
    @WithDefault("NONE")
    IdOnlyTargetCheckingLevel idOnlyTargetCheckingLevel();

    /**
     * jimmer.transactionCacheOperatorFixedDelay
     */
    @WithDefault("5s")
    String transactionCacheOperatorFixedDelay();

    /**
     * jimmer.defaultEnumStrategy
     */
    @WithDefault("NAME")
    EnumType.Strategy defaultEnumStrategy();

    /**
     * jimmer.defaultBatchSize
     */
    @WithDefault("128")
    OptionalInt defaultBatchSize();

    /**
     * jimmer.inListPaddingEnabled
     */
    @WithDefault("false")
    boolean inListPaddingEnabled();

    /**
     * jimmer.expandedInListPaddingEnabled
     */
    @WithDefault("false")
    boolean expandedInListPaddingEnabled();

    /**
     * jimmer.defaultListBatchSize
     */
    @WithDefault("16")
    OptionalInt defaultListBatchSize();

    /**
     * jimmer.offsetOptimizingThreshold
     */
    @WithDefault("2147483647")
    OptionalInt offsetOptimizingThreshold();

    /**
     * jimmer.isForeignKeyEnabledByDefault
     */
    @WithDefault("true")
    boolean isForeignKeyEnabledByDefault();

    /**
     * jimmer.maxCommandJoinCount
     */
    @WithDefault("2")
    int maxCommandJoinCount();

    /**
     * jimmer.mutationTransactionRequired
     */
    @WithDefault("false")
    boolean mutationTransactionRequired();

    /**
     * jimmer.targetTransferable
     */
    @WithDefault("false")
    boolean targetTransferable();

    /**
     * jimmer.explicitBatchEnabled
     */
    @WithDefault("false")
    boolean explicitBatchEnabled();

    /**
     * jimmer.dumbBatchAcceptable
     */
    @WithDefault("false")
    boolean dumbBatchAcceptable();

    /**
     * jimmer.constraintViolationTranslatable
     */
    @WithDefault("true")
    boolean constraintViolationTranslatable();

    /**
     * jimmer.executorContextPrefixes
     */
    Optional<List<String>> executorContextPrefixes();

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
