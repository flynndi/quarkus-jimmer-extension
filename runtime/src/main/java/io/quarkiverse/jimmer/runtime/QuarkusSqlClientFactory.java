package io.quarkiverse.jimmer.runtime;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.DraftInterceptor;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheAbandonedCallback;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.di.*;
import org.babyfish.jimmer.sql.dialect.DefaultDialect;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.filter.Filter;
import org.babyfish.jimmer.sql.kt.cfg.KCustomizer;
import org.babyfish.jimmer.sql.kt.cfg.KCustomizerKt;
import org.babyfish.jimmer.sql.kt.cfg.KInitializer;
import org.babyfish.jimmer.sql.kt.cfg.KInitializerKt;
import org.babyfish.jimmer.sql.kt.filter.KFilter;
import org.babyfish.jimmer.sql.kt.filter.impl.JavaFiltersKt;
import org.babyfish.jimmer.sql.meta.DatabaseNamingStrategy;
import org.babyfish.jimmer.sql.meta.DatabaseSchemaStrategy;
import org.babyfish.jimmer.sql.meta.DefaultDatabaseSchemaStrategy;
import org.babyfish.jimmer.sql.meta.MetaStringResolver;
import org.babyfish.jimmer.sql.runtime.*;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.runtime.cache.QuarkusCacheFactory;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkiverse.jimmer.runtime.cfg.JimmerDataSourceRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusAopProxyProvider;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusConnectionManager;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusLogicalDeletedValueGeneratorProvider;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusTransientResolverProvider;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusUserIdGeneratorProvider;
import io.quarkiverse.jimmer.runtime.dialect.DialectDetector;
import io.quarkiverse.jimmer.runtime.event.QuarkusEventDispatcher;
import io.quarkiverse.jimmer.runtime.internal.SqlClientBeans;
import io.quarkiverse.jimmer.runtime.meta.QuarkusMetaStringResolver;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkiverse.jimmer.runtime.util.JimmerJsonCodecs;
import io.quarkus.arc.ArcContainer;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import tools.jackson.databind.json.JsonMapper;

/** Builds real Jimmer clients; ArC owns the lifecycle of CDI-managed clients. */
final class QuarkusSqlClientFactory {

    private final JimmerRuntimeConfig runtimeConfig;

    private final JimmerBuildTimeConfig buildTimeConfig;

    private final DataSource dataSource;

    private final String dataSourceName;

    private final SqlClientBeans beans;

    private final Consumer<JSqlClient.Builder> block;

    private final boolean isKotlin;

    QuarkusSqlClientFactory(ArcContainer container, DataSource dataSource, String dataSourceName,
            Consumer<JSqlClient.Builder> block, boolean isKotlin) {
        this(container.select(JimmerRuntimeConfig.class).get(),
                container.select(JimmerBuildTimeConfig.class).get(),
                dataSource, new SqlClientBeans(container, dataSourceName), block, isKotlin);
    }

    QuarkusSqlClientFactory(JimmerRuntimeConfig runtimeConfig, JimmerBuildTimeConfig buildTimeConfig,
            DataSource dataSource, SqlClientBeans beans,
            Consumer<JSqlClient.Builder> block, boolean isKotlin) {
        this.beans = Objects.requireNonNull(beans);
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
        this.buildTimeConfig = Objects.requireNonNull(buildTimeConfig);
        this.dataSource = dataSource;
        this.dataSourceName = beans.dataSourceName();
        this.block = block;
        this.isKotlin = isKotlin;
        JimmerConfigValidator.validateDataSource(this.dataSourceName,
                runtimeConfig.dataSources().get(this.dataSourceName));
    }

    JSqlClientImplementor create() {
        JimmerDataSourceRuntimeConfig config = runtimeConfig.dataSources().get(dataSourceName);
        UserIdGeneratorProvider userIdGeneratorProvider = beans.optional(UserIdGeneratorProvider.class);
        LogicalDeletedValueGeneratorProvider logicalDeletedValueGeneratorProvider = beans.optional(
                LogicalDeletedValueGeneratorProvider.class);
        TransientResolverProvider transientResolverProvider = beans.optional(TransientResolverProvider.class);
        AopProxyProvider aopProxyProvider = beans.optional(AopProxyProvider.class);
        EntityManager entityManager = beans.optional(EntityManager.class);
        DatabaseSchemaStrategy databaseSchemaStrategy = beans.optional(DatabaseSchemaStrategy.class);
        DatabaseNamingStrategy databaseNamingStrategy = beans.optional(DatabaseNamingStrategy.class);
        MetaStringResolver metaStringResolver = beans.optional(MetaStringResolver.class);
        Executor executor = beans.optional(Executor.class);
        SqlFormatter sqlFormatter = beans.optional(SqlFormatter.class);
        JsonMapper jsonMapper = beans.optional(JsonMapper.class);
        CacheFactory cacheFactory = beans.optional(CacheFactory.class);
        Collection<CacheAbandonedCallback> callbacks = beans.matching(CacheAbandonedCallback.class);
        Collection<ScalarProvider<?, ?>> providers = beans.matching(
                ScalarProvider.class, Constant.SCALAR_PROVIDER_TYPE_LITERAL);
        Collection<DraftInterceptor<?, ?>> interceptors = beans.matching(
                DraftInterceptor.class, Constant.DRAFT_INTERCEPTOR_TYPE_LITERAL);
        // ArC matches raw producer return types and parameterized translator beans separately.
        Collection<ExceptionTranslator<?>> exceptionTranslators = beans.matching(
                ExceptionTranslator.class, Constant.EXCEPTION_TRANSLATOR_TYPE_LITERAL);

        JSqlClient.Builder builder = JSqlClient.newBuilder();
        builder.setUserIdGeneratorProvider(
                Objects.requireNonNullElseGet(userIdGeneratorProvider,
                        () -> new QuarkusUserIdGeneratorProvider(beans)));
        builder.setLogicalDeletedValueGeneratorProvider(Objects.requireNonNullElseGet(logicalDeletedValueGeneratorProvider,
                () -> new QuarkusLogicalDeletedValueGeneratorProvider(beans)));
        builder.setTransientResolverProvider(Objects.requireNonNullElseGet(transientResolverProvider,
                () -> new QuarkusTransientResolverProvider(beans)));
        builder.setAopProxyProvider(Objects.requireNonNullElseGet(aopProxyProvider, QuarkusAopProxyProvider::new));
        if (null != entityManager) {
            builder.setEntityManager(entityManager);
        }
        if (null != databaseNamingStrategy) {
            builder.setDatabaseNamingStrategy(databaseNamingStrategy);
        }
        builder.setDatabaseSchemaStrategy(databaseSchemaStrategy != null ? databaseSchemaStrategy
                : new DefaultDatabaseSchemaStrategy(
                        config.defaultSchema().orElse("")));
        builder.setMetaStringResolver(Objects.requireNonNullElseGet(metaStringResolver, QuarkusMetaStringResolver::new));

        Dialect configuredDialect = createConfiguredDialect(config);
        builder.setDialect(configuredDialect);
        builder.setDefaultReferenceFetchType(config.defaultReferenceFetchType());
        config.maxJoinFetchDepth().ifPresent(builder::setMaxJoinFetchDepth);
        builder.setTriggerType(buildTimeConfig.dataSources().get(dataSourceName).triggerType());
        builder.setDefaultDissociateActionCheckable(
                config.defaultDissociationActionCheckable());
        builder.setIdOnlyTargetCheckingLevel(config.idOnlyTargetCheckingLevel());
        builder.setDefaultEnumStrategy(config.defaultEnumStrategy());
        config.defaultBatchSize().ifPresent(builder::setDefaultBatchSize);
        builder.setInListPaddingEnabled(config.inListPaddingEnabled());
        builder.setExpandedInListPaddingEnabled(config.expandedInListPaddingEnabled());
        config.defaultListBatchSize().ifPresent(builder::setDefaultListBatchSize);
        builder.setDissociationLogicalDeleteEnabled(
                config.dissociationLogicalDeleteEnabled());
        config.offsetOptimizingThreshold()
                .ifPresent(builder::setOffsetOptimizingThreshold);
        builder.setReverseSortOptimizationEnabled(
                config.reverseSortOptimizationEnabled());
        builder.setForeignKeyEnabledByDefault(config.isForeignKeyEnabledByDefault());
        builder.setMaxCommandJoinCount(config.maxCommandJoinCount());
        builder.setMutationTransactionRequired(config.mutationTransactionRequired());
        builder.setTargetTransferable(config.targetTransferable());
        builder.setExplicitBatchEnabled(config.explicitBatchEnabled());
        builder.setDumbBatchAcceptable(config.dumbBatchAcceptable());
        builder.setConstraintViolationTranslatable(
                config.constraintViolationTranslatable());
        config.executorContextPrefixes()
                .ifPresent(builder::setExecutorContextPrefixes);

        if (config.showSql()) {
            builder.setExecutor(Executor.log(executor));
        } else {
            builder.setExecutor(executor);
        }
        if (sqlFormatter != null) {
            builder.setSqlFormatter(sqlFormatter);
        } else if (config.prettySql()) {
            if (config.inlineSqlVariables()) {
                builder.setSqlFormatter(SqlFormatter.INLINE_PRETTY);
            } else {
                builder.setSqlFormatter(SqlFormatter.PRETTY);
            }
        }
        // Preserve Jimmer's logging callback when the application does not supply one.
        if (callbacks.isEmpty()) {
            callbacks.add(CacheAbandonedCallback.log());
        }
        builder
                .setDatabaseValidationMode(runtimeConfig.databaseValidationMode())
                .setDefaultSerializedTypeJsonCodec(
                        jsonMapper != null ? JimmerJsonCodecs.toJsonCodecV3(jsonMapper) : null)
                .setCacheFactory(cacheFactory != null ? QuarkusCacheFactory.adapt(cacheFactory) : null)
                .addCacheAbandonedCallbacks(callbacks);

        for (ScalarProvider<?, ?> provider : providers) {
            builder.addScalarProvider(provider);
        }

        builder.addDraftInterceptors(interceptors);
        builder.addExceptionTranslators(exceptionTranslators);
        configureLanguageExtensions(builder);
        QuarkusEventDispatcher dispatcher = Objects.requireNonNullElseGet(
                beans.optional(QuarkusEventDispatcher.class),
                () -> new QuarkusEventDispatcher(beans.beanManager().getEvent()));
        builder.addInitializers(dispatcher.initializer(dataSourceName));

        builder.setMicroServiceName(buildTimeConfig.microServiceName().orElse(null));
        if (buildTimeConfig.microServiceName().isPresent()) {
            builder.setMicroServiceExchange(beans.optional(MicroServiceExchange.class));
        }

        if (null != this.block) {
            this.block.accept(builder);
        }
        Consumer<JSqlClient.Builder> beanBlock = beans.optional(Constant.J_SQL_CLIENT_BUILDER_TYPE_LITERAL);
        if (beanBlock != null) {
            beanBlock.accept(builder);
        }

        // Jimmer executes Customizer beans inside build(). Complete defaults after every user customizer,
        // so an explicit dialect avoids probing and a replacement connection manager supplies the metadata.
        builder.addCustomizers(customized -> configureConnectionAndDialect(customized, configuredDialect));
        return (JSqlClientImplementor) builder.build();
    }

    private void configureConnectionAndDialect(JSqlClient.Builder builder, Dialect configuredDialect) {
        var implementor = (JSqlClientImplementor.Builder) builder;
        ConnectionManager connectionManager = implementor.getConnectionManager();
        if (connectionManager == null) {
            connectionManager = beans.optional(ConnectionManager.class);
        }
        if (connectionManager == null) {
            // Datasources never inherit the SPI fallback to @Default: a named client must use its own datasource.
            DataSource selectedDataSource = dataSource != null ? dataSource : beans.requiredForDataSource(DataSource.class);
            connectionManager = new QuarkusConnectionManager(selectedDataSource);
        }
        builder.setConnectionManager(connectionManager);

        if (implementor.getDialect().getClass() == DefaultDialect.class) {
            Dialect dialect = beans.optional(Dialect.class);
            if (dialect == null) {
                dialect = configuredDialect;
            }
            if (dialect == null) {
                DialectDetector detector = beans.optional(DialectDetector.class);
                if (detector == null) {
                    detector = new DialectDetector.Impl(dataSource);
                }
                dialect = connectionManager.execute(detector::detectDialect);
            }
            builder.setDialect(dialect);
        }
    }

    private void configureLanguageExtensions(JSqlClient.Builder builder) {
        if (isKotlin) {
            if (!Constant.KOTLIN_SQL_AVAILABLE) {
                throw new IllegalStateException("Kotlin SQL clients require org.babyfish.jimmer:jimmer-sql-kotlin");
            }
            KotlinExtensions.configure(beans, builder);
        } else {
            builder.addFilters(beans.matching(Filter.class, Constant.FILTER_TYPE_LITERAL));
            builder.addCustomizers(beans.matching(Customizer.class));
            builder.addInitializers(beans.matching(Initializer.class));
        }
    }

    // Keep Kotlin method references out of the shared method's bytecode, even behind an isKotlin check.
    private static final class KotlinExtensions {

        private static void configure(SqlClientBeans beans, JSqlClient.Builder builder) {
            builder.addFilters(
                    beans.matching(KFilter.class, Constant.K_FILTER_TYPE_LITERAL)
                            .stream()
                            .map(JavaFiltersKt::toJavaFilter)
                            .collect(Collectors.toList()));
            builder.addCustomizers(
                    beans.matching(KCustomizer.class)
                            .stream()
                            .map(KCustomizerKt::toJavaCustomizer)
                            .collect(Collectors.toList()));
            builder.addInitializers(
                    beans.matching(KInitializer.class)
                            .stream()
                            .map(KInitializerKt::toJavaInitializer)
                            .collect(Collectors.toList()));
        }
    }

    @Nullable
    private Dialect createConfiguredDialect(JimmerDataSourceRuntimeConfig jimmerDataSourceRuntimeConfig) {
        if (jimmerDataSourceRuntimeConfig.dialect().isEmpty()) {
            return null;
        }
        String className = jimmerDataSourceRuntimeConfig.dialect().get();
        String property = DataSourceUtil.isDefault(dataSourceName) ? "quarkus.jimmer.dialect"
                : "quarkus.jimmer.\"" + dataSourceName.replace("\"", "\\\"") + "\".dialect";
        Class<?> clazz;
        try {
            clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException(
                    "The class \"" + className + "\" specified by `" + property + "` cannot be found");
        }
        if (!Dialect.class.isAssignableFrom(clazz) || clazz.isInterface()) {
            throw new IllegalArgumentException(
                    "The class \"" + className + "\" specified by `" + property + "` must be a valid dialect implementation");
        }
        try {
            return (Dialect) clazz.getConstructor().newInstance();
        } catch (InvocationTargetException ex) {
            throw new IllegalArgumentException(
                    "Cannot create instance for the class \"" + className + "\" specified by `" + property + "`",
                    ex.getTargetException());
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Cannot create instance for the class \"" + className + "\" specified by `" + property + "`",
                    ex);
        }
    }
}
