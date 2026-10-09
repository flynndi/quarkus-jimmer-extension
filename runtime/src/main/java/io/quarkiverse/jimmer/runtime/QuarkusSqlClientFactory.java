package io.quarkiverse.jimmer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.util.TypeLiteral;

import org.babyfish.jimmer.sql.DraftInterceptor;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheAbandonedCallback;
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

import com.fasterxml.jackson.databind.ObjectMapper;

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
import io.quarkiverse.jimmer.runtime.meta.QuarkusMetaStringResolver;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkiverse.jimmer.runtime.util.JimmerJsonCodecs;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

/** Builds real Jimmer clients; ArC owns the lifecycle of CDI-managed clients. */
final class QuarkusSqlClientFactory {

    private final JimmerRuntimeConfig runtimeConfig;

    private final JimmerBuildTimeConfig buildTimeConfig;

    private final DataSource dataSource;

    private final String dataSourceName;

    private final ArcContainer container;

    private final Consumer<JSqlClient.Builder> block;

    private final boolean isKotlin;

    QuarkusSqlClientFactory(ArcContainer container, DataSource dataSource, String dataSourceName,
            Consumer<JSqlClient.Builder> block, boolean isKotlin) {
        this(container, container.select(JimmerRuntimeConfig.class).get(),
                container.select(JimmerBuildTimeConfig.class).get(), dataSource, dataSourceName, block, isKotlin);
    }

    QuarkusSqlClientFactory(ArcContainer container, JimmerRuntimeConfig runtimeConfig,
            JimmerBuildTimeConfig buildTimeConfig, DataSource dataSource, String dataSourceName,
            Consumer<JSqlClient.Builder> block, boolean isKotlin) {
        this.container = Objects.requireNonNull(container);
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
        this.buildTimeConfig = Objects.requireNonNull(buildTimeConfig);
        this.dataSource = dataSource;
        this.dataSourceName = dataSourceName != null ? dataSourceName : DataSourceUtil.DEFAULT_DATASOURCE_NAME;
        this.block = block;
        this.isKotlin = isKotlin;
        JimmerConfigValidator.validateDataSource(this.dataSourceName,
                runtimeConfig.dataSources().get(this.dataSourceName));
    }

    JSqlClientImplementor create() {
        JimmerDataSourceRuntimeConfig config = runtimeConfig.dataSources().get(dataSourceName);
        UserIdGeneratorProvider userIdGeneratorProvider = getOptionalBean(UserIdGeneratorProvider.class);
        LogicalDeletedValueGeneratorProvider logicalDeletedValueGeneratorProvider = getOptionalBean(
                LogicalDeletedValueGeneratorProvider.class);
        TransientResolverProvider transientResolverProvider = getOptionalBean(TransientResolverProvider.class);
        AopProxyProvider aopProxyProvider = getOptionalBean(AopProxyProvider.class);
        EntityManager entityManager = getOptionalBean(EntityManager.class);
        DatabaseSchemaStrategy databaseSchemaStrategy = getOptionalBean(DatabaseSchemaStrategy.class);
        DatabaseNamingStrategy databaseNamingStrategy = getOptionalBean(DatabaseNamingStrategy.class);
        MetaStringResolver metaStringResolver = getOptionalBean(MetaStringResolver.class);
        Executor executor = getOptionalBean(Executor.class);
        SqlFormatter sqlFormatter = getOptionalBean(SqlFormatter.class);
        ObjectMapper objectMapper = getOptionalBean(ObjectMapper.class);
        Collection<CacheAbandonedCallback> callbacks = getMatchingBeans(CacheAbandonedCallback.class);
        Collection<ScalarProvider<?, ?>> providers = getMatchingBeans(Constant.SCALAR_PROVIDER_TYPE_LITERAL.getType());
        Collection<DraftInterceptor<?, ?>> interceptors = getMatchingBeans(Constant.DRAFT_INTERCEPTOR_TYPE_LITERAL.getType());
        Collection<ExceptionTranslator<?>> exceptionTranslators = getMatchingBeans(ExceptionTranslator.class);

        JSqlClient.Builder builder = JSqlClient.newBuilder();
        builder.setUserIdGeneratorProvider(
                Objects.requireNonNullElseGet(userIdGeneratorProvider, () -> new QuarkusUserIdGeneratorProvider(container)));
        builder.setLogicalDeletedValueGeneratorProvider(Objects.requireNonNullElseGet(logicalDeletedValueGeneratorProvider,
                () -> new QuarkusLogicalDeletedValueGeneratorProvider(container)));
        builder.setTransientResolverProvider(Objects.requireNonNullElseGet(transientResolverProvider,
                () -> new QuarkusTransientResolverProvider(container)));
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
                        objectMapper != null ? JimmerJsonCodecs.toJsonCodecV2(objectMapper) : null)
                .addCacheAbandonedCallbacks(callbacks);

        for (ScalarProvider<?, ?> provider : providers) {
            builder.addScalarProvider(provider);
        }

        builder.addDraftInterceptors(interceptors);
        builder.addExceptionTranslators(exceptionTranslators);
        configureLanguageExtensions(builder);
        var dispatchers = container.select(QuarkusEventDispatcher.class);
        // Enabled integration requires the generated bean's event injection points to preserve notification provenance.
        QuarkusEventDispatcher dispatcher = dispatchers.isUnsatisfied() && !buildTimeConfig.enable()
                ? new QuarkusEventDispatcher(container.beanManager().getEvent())
                : dispatchers.get();
        builder.addInitializers(dispatcher.initializer(dataSourceName));

        builder.setMicroServiceName(buildTimeConfig.microServiceName().orElse(null));
        if (buildTimeConfig.microServiceName().isPresent()) {
            builder.setMicroServiceExchange(getOptionalBean(MicroServiceExchange.class));
        }

        if (null != this.block) {
            this.block.accept(builder);
        }
        Consumer<JSqlClient.Builder> beanBlock = getOptionalBean(Constant.J_SQL_CLIENT_BUILDER_TYPE_LITERAL);
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
            connectionManager = getOptionalBean(ConnectionManager.class);
        }
        if (connectionManager == null) {
            // Datasources never inherit the SPI fallback to @Default: a named client must use its own datasource.
            DataSource selectedDataSource = dataSource != null ? dataSource
                    : container.select(DataSource.class, DataSourceUtil.isDefault(dataSourceName)
                            ? Default.Literal.INSTANCE
                            : new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName)).get();
            connectionManager = new QuarkusConnectionManager(selectedDataSource);
        }
        builder.setConnectionManager(connectionManager);

        if (implementor.getDialect().getClass() == DefaultDialect.class) {
            Dialect dialect = getOptionalBean(Dialect.class);
            if (dialect == null) {
                dialect = configuredDialect;
            }
            if (dialect == null) {
                DialectDetector detector = getOptionalBean(DialectDetector.class);
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
            builder.addFilters(
                    this.<KFilter<?>> getMatchingBeans(Constant.K_FILTER_TYPE_LITERAL.getType())
                            .stream()
                            .map(JavaFiltersKt::toJavaFilter)
                            .collect(Collectors.toList()));
            builder.addCustomizers(
                    this.<KCustomizer> getMatchingBeans(KCustomizer.class)
                            .stream()
                            .map(KCustomizerKt::toJavaCustomizer)
                            .collect(Collectors.toList()));
            builder.addInitializers(
                    this.<KInitializer> getMatchingBeans(KInitializer.class)
                            .stream()
                            .map(KInitializerKt::toJavaInitializer)
                            .collect(Collectors.toList()));
        } else {
            builder.addFilters(this.<Filter<?>> getMatchingBeans(Constant.FILTER_TYPE_LITERAL.getType()));
            builder.addCustomizers(this.<Customizer> getMatchingBeans(Customizer.class));
            builder.addInitializers(this.<Initializer> getMatchingBeans(Initializer.class));
        }
    }

    private <T> T getOptionalBean(Class<T> type) {
        var named = container.select(type, new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName));
        if (!named.isUnsatisfied()) {
            return named.get();
        }
        var defaults = container.select(type);
        return defaults.isUnsatisfied() ? null : defaults.get();
    }

    @SuppressWarnings("unchecked")
    private <E> Collection<E> getMatchingBeans(Type elementType) {
        Collection<E> collection = new ArrayList<>();
        for (InstanceHandle<?> instanceHandle : container.listAll(elementType)) {
            Optional<Annotation> qualifier = instanceHandle.getBean().getQualifiers().stream()
                    .filter(annotation -> annotation.annotationType() == io.quarkus.agroal.DataSource.class)
                    .findFirst();
            // Inspect qualifiers before get(): unrelated datasource beans must not be instantiated.
            if (qualifier.isEmpty()
                    || dataSourceName.equals(((io.quarkus.agroal.DataSource) qualifier.get()).value())) {
                E value = (E) instanceHandle.get();
                if (value != null) {
                    collection.add(value);
                }
            }
        }
        return collection;
    }

    private <E> E getOptionalBean(TypeLiteral<E> typeLiteral) {
        var named = container.select(typeLiteral, new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName));
        if (!named.isUnsatisfied()) {
            return named.get();
        }
        var defaults = container.select(typeLiteral);
        return defaults.isUnsatisfied() ? null : defaults.get();
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
