package io.quarkiverse.jimmer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.interceptor.InvocationContext;

import org.babyfish.jimmer.impl.util.ObjectUtil;
import org.babyfish.jimmer.sql.DraftInterceptor;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheAbandonedCallback;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.CacheOperator;
import org.babyfish.jimmer.sql.di.*;
import org.babyfish.jimmer.sql.dialect.DefaultDialect;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.event.TriggerType;
import org.babyfish.jimmer.sql.event.Triggers;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerDataSourceRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusConnectionManager;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusLogicalDeletedValueGeneratorProvider;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusTransientResolverProvider;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusUserIdGeneratorProvider;
import io.quarkiverse.jimmer.runtime.dialect.DialectDetector;
import io.quarkiverse.jimmer.runtime.meta.QuarkusMetaStringResolver;
import io.quarkiverse.jimmer.runtime.util.Assert;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

class JQuarkusSqlClient extends JLazyInitializationSqlClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JQuarkusSqlClient.class);

    private final DataSource dataSource;

    private final String dataSourceName;

    private final ArcContainer container;

    private final Consumer<JSqlClient.Builder> block;

    private final boolean isKotlin;

    public JQuarkusSqlClient(ArcContainer container, DataSource dataSource, String dataSourceName,
            Consumer<JSqlClient.Builder> block, boolean isKotlin) {
        this.container = Objects.requireNonNullElseGet(container, Arc::container);
        this.dataSource = dataSource;
        this.dataSourceName = dataSourceName;
        this.block = block;
        this.isKotlin = isKotlin;
    }

    @Override
    protected JSqlClient.Builder createBuilder() {

        JimmerRuntimeConfig runtimeConfig = getOptionalBean(JimmerRuntimeConfig.class);
        JimmerBuildTimeConfig buildTimeConfig = getOptionalBean(JimmerBuildTimeConfig.class);
        Assert.notNull(runtimeConfig, "JimmerRuntimeConfig must not be null!");
        Assert.notNull(buildTimeConfig, "JimmerBuildTimeConfig must not be null!");
        UserIdGeneratorProvider userIdGeneratorProvider = getOptionalBean(UserIdGeneratorProvider.class);
        LogicalDeletedValueGeneratorProvider logicalDeletedValueGeneratorProvider = getOptionalBean(
                LogicalDeletedValueGeneratorProvider.class);
        TransientResolverProvider transientResolverProvider = getOptionalBean(TransientResolverProvider.class);
        AopProxyProvider aopProxyProvider = getOptionalBean(AopProxyProvider.class);
        EntityManager entityManager = getOptionalBean(EntityManager.class);
        DatabaseSchemaStrategy databaseSchemaStrategy = getOptionalBean(DatabaseSchemaStrategy.class);
        DatabaseNamingStrategy databaseNamingStrategy = getOptionalBean(DatabaseNamingStrategy.class);
        MetaStringResolver metaStringResolver = getOptionalBean(MetaStringResolver.class);
        Dialect dialect = getOptionalBean(Dialect.class);
        DialectDetector dialectDetector = getOptionalBean(DialectDetector.class);
        Executor executor = getOptionalBean(Executor.class);
        SqlFormatter sqlFormatter = getOptionalBean(SqlFormatter.class);
        ObjectMapper objectMapper = getOptionalBean(ObjectMapper.class);
        CacheFactory cacheFactory = getOptionalBean(CacheFactory.class);
        CacheOperator cacheOperator = getOptionalBean(CacheOperator.class, dataSourceName);
        MicroServiceExchange exchange = getOptionalBean(MicroServiceExchange.class);
        Collection<CacheAbandonedCallback> callbacks = getObjects(CacheAbandonedCallback.class);
        Consumer<JSqlClient.Builder> block = getObject(Constant.J_SQL_CLIENT_BUILDER_TYPE_LITERAL);
        Collection<ScalarProvider<?, ?>> providers = getObjects(Constant.SCALAR_PROVIDER_TYPE_LITERAL);
        Collection<DraftInterceptor<?, ?>> interceptors = getObjects(Constant.DRAFT_INTERCEPTOR_TYPE_LITERAL);
        Collection<ExceptionTranslator<?>> exceptionTranslators = getObjects(ExceptionTranslator.class);

        JSqlClient.Builder builder = JSqlClient.newBuilder();
        builder.setUserIdGeneratorProvider(
                Objects.requireNonNullElseGet(userIdGeneratorProvider, () -> new QuarkusUserIdGeneratorProvider(container)));
        builder.setLogicalDeletedValueGeneratorProvider(Objects.requireNonNullElseGet(logicalDeletedValueGeneratorProvider,
                () -> new QuarkusLogicalDeletedValueGeneratorProvider(container)));
        builder.setTransientResolverProvider(Objects.requireNonNullElseGet(transientResolverProvider,
                () -> new QuarkusTransientResolverProvider(container)));
        builder.setAopProxyProvider(Objects.requireNonNullElseGet(aopProxyProvider, () -> this::getTargetClass));
        if (null != entityManager) {
            builder.setEntityManager(entityManager);
        }
        if (null != databaseNamingStrategy) {
            builder.setDatabaseNamingStrategy(databaseNamingStrategy);
        } else if (runtimeConfig.dataSources().get(dataSourceName).defaultSchema().isPresent()) {
            builder.setDatabaseSchemaStrategy(
                    new DefaultDatabaseSchemaStrategy(runtimeConfig.dataSources().get(dataSourceName).defaultSchema().get()));
        }
        builder.setDatabaseSchemaStrategy(databaseSchemaStrategy != null ? databaseSchemaStrategy
                : new DefaultDatabaseSchemaStrategy(
                        runtimeConfig.dataSources().get(dataSourceName).defaultSchema().orElse("")));
        builder.setMetaStringResolver(Objects.requireNonNullElseGet(metaStringResolver, QuarkusMetaStringResolver::new));

        builder.setDialect(this.initializeDialect(runtimeConfig));
        builder.setDefaultReferenceFetchType(runtimeConfig.dataSources().get(dataSourceName).defaultReferenceFetchType());
        runtimeConfig.dataSources().get(dataSourceName).maxJoinFetchDepth().ifPresent(builder::setMaxJoinFetchDepth);
        builder.setTriggerType(buildTimeConfig.dataSources().get(dataSourceName).triggerType());
        builder.setDefaultDissociateActionCheckable(
                runtimeConfig.dataSources().get(dataSourceName).defaultDissociationActionCheckable());
        builder.setIdOnlyTargetCheckingLevel(runtimeConfig.dataSources().get(dataSourceName).idOnlyTargetCheckingLevel());
        builder.setDefaultEnumStrategy(runtimeConfig.dataSources().get(dataSourceName).defaultEnumStrategy());
        runtimeConfig.dataSources().get(dataSourceName).defaultBatchSize().ifPresent(builder::setDefaultBatchSize);
        builder.setInListPaddingEnabled(runtimeConfig.dataSources().get(dataSourceName).inListPaddingEnabled());
        builder.setExpandedInListPaddingEnabled(runtimeConfig.dataSources().get(dataSourceName).expandedInListPaddingEnabled());
        runtimeConfig.dataSources().get(dataSourceName).defaultListBatchSize().ifPresent(builder::setDefaultListBatchSize);
        runtimeConfig.dataSources().get(dataSourceName).offsetOptimizingThreshold()
                .ifPresent(builder::setOffsetOptimizingThreshold);
        builder.setReverseSortOptimizationEnabled(
                runtimeConfig.dataSources().get(dataSourceName).reverseSortOptimizationEnabled());
        builder.setForeignKeyEnabledByDefault(runtimeConfig.dataSources().get(dataSourceName).isForeignKeyEnabledByDefault());
        builder.setMaxCommandJoinCount(runtimeConfig.dataSources().get(dataSourceName).maxCommandJoinCount());
        builder.setMutationTransactionRequired(runtimeConfig.dataSources().get(dataSourceName).mutationTransactionRequired());
        builder.setTargetTransferable(runtimeConfig.dataSources().get(dataSourceName).targetTransferable());
        builder.setExplicitBatchEnabled(runtimeConfig.dataSources().get(dataSourceName).explicitBatchEnabled());
        builder.setDumbBatchAcceptable(runtimeConfig.dataSources().get(dataSourceName).dumbBatchAcceptable());
        builder.setConstraintViolationTranslatable(
                runtimeConfig.dataSources().get(dataSourceName).constraintViolationTranslatable());
        runtimeConfig.dataSources().get(dataSourceName).executorContextPrefixes()
                .ifPresent(builder::setExecutorContextPrefixes);

        if (buildTimeConfig.dataSources().get(dataSourceName).showSql()) {
            builder.setExecutor(Executor.log(executor));
        } else {
            builder.setExecutor(executor);
        }
        if (sqlFormatter != null) {
            builder.setSqlFormatter(sqlFormatter);
        } else if (buildTimeConfig.dataSources().get(dataSourceName).prettySql()) {
            if (buildTimeConfig.dataSources().get(dataSourceName).inlineSqlVariables()) {
                builder.setSqlFormatter(SqlFormatter.INLINE_PRETTY);
            } else {
                builder.setSqlFormatter(SqlFormatter.PRETTY);
            }
        }
        // Special handling in quarkus, if there is no user-defined bean, one is generated by default
        if (callbacks.isEmpty()) {
            callbacks.add(CacheAbandonedCallback.log());
        }
        builder
                .setDatabaseValidationMode(runtimeConfig.databaseValidation().mode())
                .setDefaultSerializedTypeObjectMapper(objectMapper)
                .setCacheFactory(cacheFactory)
                .setCacheOperator(cacheOperator)
                .addCacheAbandonedCallbacks(callbacks);

        for (ScalarProvider<?, ?> provider : providers) {
            builder.addScalarProvider(provider);
        }

        builder.addDraftInterceptors(interceptors);
        builder.addExceptionTranslators(exceptionTranslators);
        initializeByLanguage(builder);
        builder.addInitializers(new QuarkusEventInitializer());

        builder.setMicroServiceName(buildTimeConfig.microServiceName().orElse(null));
        if (buildTimeConfig.microServiceName().isPresent()) {
            builder.setMicroServiceExchange(exchange);
        }

        if (null != this.block) {
            this.block.accept(builder);
        }
        if (null != block) {
            block.accept(builder);
        }

        ConnectionManager connectionManager = ObjectUtil.firstNonNullOf(
                () -> ((JSqlClientImplementor.Builder) builder).getConnectionManager(),
                () -> getOptionalBean(ConnectionManager.class),
                () -> dataSource == null ? null
                        : new QuarkusConnectionManager(dataSource),
                () -> new QuarkusConnectionManager(getOptionalBean(DataSource.class)));

        builder.setConnectionManager(connectionManager);

        if (((JSqlClientImplementor.Builder) builder).getDialect().getClass() == DefaultDialect.class) {
            DialectDetector finalDetector = dialectDetector != null ? dialectDetector : new DialectDetector.Impl(dataSource);
            builder.setDialect(ObjectUtil.optionalFirstNonNullOf(() -> dialect, () -> this.initializeDialect(runtimeConfig),
                    () -> connectionManager.execute(finalDetector::detectDialect)));
        }

        return builder;
    }

    private void initializeByLanguage(JSqlClient.Builder builder) {

        Collection<Filter<?>> javaFilters = getObjects(Constant.FILTER_TYPE_LITERAL);
        Collection<Customizer> javaCustomizers = getObjects(Customizer.class);
        Collection<Initializer> javaInitializers = getObjects(Initializer.class);
        Collection<KFilter<?>> kotlinFilters = getObjects(Constant.K_FILTER_TYPE_LITERAL);
        Collection<KCustomizer> kotlinCustomizers = getObjects(KCustomizer.class);
        Collection<KInitializer> kotlinInitializers = getObjects(KInitializer.class);

        if (isKotlin) {
            if (!javaFilters.isEmpty()) {
                LOGGER.warn(
                        "Jimmer is working in kotlin mode, but some java filters " +
                                "has been found in quarkus context, they will be ignored");
            }
            if (!javaCustomizers.isEmpty()) {
                LOGGER.warn(
                        "Jimmer is working in kotlin mode, but some java customizers " +
                                "has been found in quarkus context, they will be ignored");
            }
            if (!javaInitializers.isEmpty()) {
                LOGGER.warn(
                        "Jimmer is working in kotlin mode, but some java initializers " +
                                "has been found in quarkus context, they will be ignored");
            }
            builder.addFilters(
                    kotlinFilters
                            .stream()
                            .map(JavaFiltersKt::toJavaFilter)
                            .collect(Collectors.toList()));
            builder.addCustomizers(
                    kotlinCustomizers
                            .stream()
                            .map(KCustomizerKt::toJavaCustomizer)
                            .collect(Collectors.toList()));
            builder.addInitializers(
                    kotlinInitializers
                            .stream()
                            .map(KInitializerKt::toJavaInitializer)
                            .collect(Collectors.toList()));
        } else {
            if (!kotlinFilters.isEmpty()) {
                LOGGER.warn(
                        "Jimmer is working in java mode, but some kotlin filters " +
                                "has been found in quarkus context, they will be ignored");
            }
            if (!kotlinCustomizers.isEmpty()) {
                LOGGER.warn(
                        "Jimmer is working in java mode, but some kotlin customizers " +
                                "has been found in quarkus context, they will be ignored");
            }
            if (!kotlinInitializers.isEmpty()) {
                LOGGER.warn(
                        "Jimmer is working in kotlin mode, but some kotlin initializers " +
                                "has been found in quarkus context, they will be ignored");
            }
            builder.addFilters(javaFilters);
            builder.addCustomizers(javaCustomizers);
            builder.addInitializers(javaInitializers);
        }
    }

    private <T> T getOptionalBean(Class<T> type) {
        if (container.instance(type).isAvailable()) {
            return container.instance(type).get();
        } else if (container
                .instance(type, new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName))
                .isAvailable()) {
            return container
                    .instance(type, new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName))
                    .get();
        } else {
            return null;
        }
    }

    private <T> T getOptionalBean(Class<T> type, String dataSourceName) {
        if (container.instance(type, new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName))
                .isAvailable()) {
            return container
                    .instance(type, new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName))
                    .get();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <E> Collection<E> getObjects(Class<?> elementType) {
        Collection<E> collection = new ArrayList<>();
        for (InstanceHandle<?> instanceHandle : container.listAll(elementType)) {
            if (instanceHandle.isAvailable()) {
                Optional<Annotation> annotationOptional = instanceHandle.getBean().getQualifiers().stream()
                        .filter(x -> x.annotationType().equals(io.quarkus.agroal.DataSource.class)).findFirst();
                if (annotationOptional.isPresent()) {
                    if (dataSourceName.equals(((io.quarkus.agroal.DataSource) annotationOptional.get()).value())) {
                        collection.add((E) instanceHandle.get());
                    }
                } else {
                    collection.add((E) instanceHandle.get());
                }
            }
        }
        return collection;
    }

    @SuppressWarnings("unchecked")
    private <E> Collection<E> getObjects(TypeLiteral<?> typeLiteral) {
        Collection<E> collection = new ArrayList<>();
        for (InstanceHandle<?> instanceHandle : container.listAll(typeLiteral)) {
            if (instanceHandle.isAvailable()) {
                Optional<Annotation> annotationOptional = instanceHandle.getBean().getQualifiers().stream()
                        .filter(x -> x.annotationType().equals(io.quarkus.agroal.DataSource.class)).findFirst();
                if (annotationOptional.isPresent()) {
                    if (dataSourceName.equals(((io.quarkus.agroal.DataSource) annotationOptional.get()).value())) {
                        collection.add((E) instanceHandle.get());
                    }
                } else {
                    collection.add((E) instanceHandle.get());
                }
            }
        }
        return collection;
    }

    @SuppressWarnings("unchecked")
    private <E> E getObject(TypeLiteral<?> typeLiteral) {
        for (InstanceHandle<?> instanceHandle : container.listAll(typeLiteral)) {
            if (instanceHandle.isAvailable()) {
                Optional<Annotation> annotationOptional = instanceHandle.getBean().getQualifiers().stream()
                        .filter(x -> x.annotationType().equals(io.quarkus.agroal.DataSource.class)).findFirst();
                if (annotationOptional.isPresent()) {
                    if (dataSourceName.equals(((io.quarkus.agroal.DataSource) annotationOptional.get()).value())) {
                        return ((E) instanceHandle.get());
                    }
                } else {
                    return ((E) instanceHandle.get());
                }
            }
        }
        return null;
    }

    @Nullable
    private Dialect initializeDialect(JimmerRuntimeConfig config) {
        Dialect dialect;
        JimmerDataSourceRuntimeConfig jimmerDataSourceRuntimeConfig = config.dataSources().get(dataSourceName);
        if (jimmerDataSourceRuntimeConfig.dialect().isEmpty()) {
            return null;
        } else {
            Class<?> clazz;
            try {
                clazz = Class.forName(jimmerDataSourceRuntimeConfig.dialect().get(), true,
                        Thread.currentThread().getContextClassLoader());
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException(
                        "The class \"" + jimmerDataSourceRuntimeConfig.dialect().get()
                                + "\" specified by `quarkus.jimmer.dialect` cannot be found");
            }
            if (!Dialect.class.isAssignableFrom(clazz) || clazz.isInterface()) {
                throw new IllegalArgumentException(
                        "The class \"" + jimmerDataSourceRuntimeConfig.dialect().get()
                                + "\" specified by `quarkus.jimmer.dialect` must be a valid dialect implementation");
            }
            try {
                dialect = (Dialect) clazz.getConstructor().newInstance();
            } catch (InvocationTargetException ex) {
                throw new IllegalArgumentException(
                        "Create create instance for the class \"" + jimmerDataSourceRuntimeConfig.dialect().get()
                                + "\" specified by `quarkus.jimmer.dialect`",
                        ex.getTargetException());
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        "Create create instance for the class \"" + jimmerDataSourceRuntimeConfig.dialect().get()
                                + "\" specified by `quarkus.jimmer.dialect`",
                        ex);
            }
        }
        return dialect;
    }

    private static class QuarkusEventInitializer implements Initializer {

        @Override
        public void initialize(JSqlClient sqlClient) {
            Triggers[] triggersArr = ((JSqlClientImplementor) sqlClient).getTriggerType() == TriggerType.BOTH
                    ? new Triggers[] { sqlClient.getTriggers(), sqlClient.getTriggers(true) }
                    : new Triggers[] { sqlClient.getTriggers() };
            Event<Object> event = Arc.container().beanManager().getEvent();
            Event<EntityEvent<?>> entityEvent = event.select(new TypeLiteral<>() {
            });
            Event<AssociationEvent> associationEvent = event.select(new TypeLiteral<>() {
            });
            for (Triggers triggers : triggersArr) {
                triggers.addEntityListener(entityEvent::fire);
                triggers.addAssociationListener(associationEvent::fire);
            }
        }
    }

    private Class<?> getTargetClass(Object instance) {
        if (instance instanceof InvocationContext) {
            return ((InvocationContext) instance).getMethod().getClass();
        } else {
            return instance.getClass();
        }
    }
}
