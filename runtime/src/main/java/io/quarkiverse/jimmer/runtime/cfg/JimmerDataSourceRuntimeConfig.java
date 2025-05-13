package io.quarkiverse.jimmer.runtime.cfg;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.babyfish.jimmer.sql.EnumType;
import org.babyfish.jimmer.sql.fetcher.ReferenceFetchType;
import org.babyfish.jimmer.sql.runtime.IdOnlyTargetCheckingLevel;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface JimmerDataSourceRuntimeConfig {

    /**
     * Flag to activate/deactivate Jimmer for a specific datasource at runtime.
     */
    @ConfigDocDefault("'true' if the datasource is active; 'false' otherwise")
    Optional<Boolean> active();

    /**
     * jimmer.dialect
     */
    Optional<String> dialect();

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
     * jimmer.defaultListBatchSize
     */
    @WithDefault("false")
    boolean reverseSortOptimizationEnabled();

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
}
