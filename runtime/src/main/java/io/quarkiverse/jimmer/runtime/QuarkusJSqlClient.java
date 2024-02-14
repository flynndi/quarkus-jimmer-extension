package io.quarkiverse.jimmer.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.interceptor.InvocationContext;

import org.babyfish.jimmer.sql.DraftInterceptor;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheAbandonedCallback;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.CacheOperator;
import org.babyfish.jimmer.sql.di.*;
import org.babyfish.jimmer.sql.dialect.Dialect;
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
import org.babyfish.jimmer.sql.runtime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusLogicalDeletedValueGeneratorProvider;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusTransientResolverProvider;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusUserIdGeneratorProvider;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkiverse.jimmer.runtime.util.QuarkusJSqlClientContainerUtil;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

public class QuarkusJSqlClient extends JLazyInitializationSqlClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuarkusJSqlClient.class);

    private final JimmerBuildTimeConfig config;

    private final DataSource dataSource;

    private final String dataSourceName;

    private final ArcContainer container;

    private final Event<Object> event;

    private final Dialect dialect;

    private final boolean isKotlin;

    public QuarkusJSqlClient(JimmerBuildTimeConfig config, DataSource dataSource, String dataSourceName,
            ArcContainer container, Event<Object> event, Dialect dialect, boolean isKotlin) {
        this.config = config;
        this.dataSource = dataSource;
        this.dataSourceName = dataSourceName;
        this.container = container;
        this.event = event;
        this.dialect = dialect;
        this.isKotlin = isKotlin;
    }

    @Override
    protected Builder createBuilder() {

        ConnectionManager connectionManager = getOptionalBean(ConnectionManager.class);
        UserIdGeneratorProvider userIdGeneratorProvider = getOptionalBean(UserIdGeneratorProvider.class);
        LogicalDeletedValueGeneratorProvider logicalDeletedValueGeneratorProvider = getOptionalBean(
                LogicalDeletedValueGeneratorProvider.class);
        TransientResolverProvider transientResolverProvider = getOptionalBean(TransientResolverProvider.class);
        AopProxyProvider aopProxyProvider = getOptionalBean(AopProxyProvider.class);
        EntityManager entityManager = getOptionalBean(EntityManager.class);
        DatabaseNamingStrategy databaseNamingStrategy = getOptionalBean(DatabaseNamingStrategy.class);
        Executor executor = getOptionalBean(Executor.class);
        SqlFormatter sqlFormatter = getOptionalBean(SqlFormatter.class);
        CacheFactory cacheFactory = getOptionalBean(CacheFactory.class);
        CacheOperator cacheOperator = getOptionalBean(CacheOperator.class, dataSourceName);
        MicroServiceExchange exchange = getOptionalBean(MicroServiceExchange.class);
        Collection<CacheAbandonedCallback> callbacks = getObjects(CacheAbandonedCallback.class);
        Collection<ScalarProvider<?, ?>> providers = getObjects(Constant.SCALAR_PROVIDER_TYPE_LITERAL);
        Collection<DraftInterceptor<?, ?>> interceptors = getObjects(Constant.DRAFT_INTERCEPTOR_TYPE_LITERAL);

        Builder builder = JSqlClient.newBuilder();
        if (null != connectionManager) {
            builder.setConnectionManager(connectionManager);
        } else if (null != dataSource) {
            builder.setConnectionManager(ConnectionManager.simpleConnectionManager(dataSource));
        }
        if (null != userIdGeneratorProvider) {
            builder.setUserIdGeneratorProvider(userIdGeneratorProvider);
        } else {
            builder.setUserIdGeneratorProvider(new QuarkusUserIdGeneratorProvider(container));
        }
        if (null != logicalDeletedValueGeneratorProvider) {
            builder.setLogicalDeletedValueGeneratorProvider(logicalDeletedValueGeneratorProvider);
        } else {
            builder.setLogicalDeletedValueGeneratorProvider(new QuarkusLogicalDeletedValueGeneratorProvider(container));
        }
        if (null != transientResolverProvider) {
            builder.setTransientResolverProvider(transientResolverProvider);
        } else {
            builder.setTransientResolverProvider(new QuarkusTransientResolverProvider(container));
        }
        if (null != aopProxyProvider) {
            builder.setAopProxyProvider(aopProxyProvider);
        } else {
            builder.setAopProxyProvider(this::getTargetClass);
        }
        if (null != entityManager) {
            builder.setEntityManager(entityManager);
        }
        if (null != databaseNamingStrategy) {
            builder.setDatabaseNamingStrategy(databaseNamingStrategy);
        }

        builder.setDialect(this.dialect);
        builder.setTriggerType(config.triggerType);
        builder.setDefaultDissociateActionCheckable(config.defaultDissociationActionCheckable);
        builder.setIdOnlyTargetCheckingLevel(config.idOnlyTargetCheckingLevel);
        builder.setDefaultEnumStrategy(config.defaultEnumStrategy);
        config.defaultBatchSize.ifPresent(builder::setDefaultBatchSize);
        config.defaultListBatchSize.ifPresent(builder::setDefaultListBatchSize);
        config.offsetOptimizingThreshold.ifPresent(builder::setOffsetOptimizingThreshold);
        builder.setForeignKeyEnabledByDefault(config.isForeignKeyEnabledByDefault);
        builder.setSaveCommandPessimisticLock(config.saveCommandPessimisticLock);
        config.executorContextPrefixes.ifPresent(builder::setExecutorContextPrefixes);

        if (config.showSql) {
            builder.setExecutor(Executor.log(executor));
        } else {
            builder.setExecutor(executor);
        }
        if (sqlFormatter != null) {
            builder.setSqlFormatter(sqlFormatter);
        } else if (config.prettySql) {
            if (config.inlineSqlVariables) {
                builder.setSqlFormatter(SqlFormatter.INLINE_PRETTY);
            } else {
                builder.setSqlFormatter(SqlFormatter.PRETTY);
            }
        }
        builder
                .setDatabaseValidationMode(config.databaseValidation.mode)
                .setDatabaseValidationCatalog(config.databaseValidation.catalog.orElse(null))
                .setDatabaseValidationSchema(config.databaseValidation.schema.orElse(null))
                .setCacheFactory(cacheFactory)
                .setCacheOperator(cacheOperator)
                .addCacheAbandonedCallbacks(callbacks);

        for (ScalarProvider<?, ?> provider : providers) {
            builder.addScalarProvider(provider);
        }

        builder.addDraftInterceptors(interceptors);
        initializeByLanguage(builder);
        builder.addInitializers(new QuarkusEventInitializer(event));

        builder.setMicroServiceName(config.microServiceName.orElse(null));
        if (config.microServiceName.isPresent()) {
            builder.setMicroServiceExchange(exchange);
        }

        return builder;
    }

    private void initializeByLanguage(Builder builder) {

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
        }
        return null;
    }

    private <T> T getOptionalBean(Class<T> type, String dataSourceName) {
        if (container.instance(type, QuarkusJSqlClientContainerUtil.getQuarkusJSqlClientContainerQualifier(dataSourceName))
                .isAvailable()) {
            return container.instance(type).get();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <E> Collection<E> getObjects(Class<?> elementType) {
        Collection<E> collection = new ArrayList<>();
        for (InstanceHandle<?> instanceHandle : container.listAll(elementType)) {
            collection.add((E) instanceHandle.get());
        }
        return collection;
    }

    @SuppressWarnings("unchecked")
    private <E> Collection<E> getObjects(TypeLiteral<?> typeLiteral) {
        Collection<E> collection = new ArrayList<>();
        for (InstanceHandle<?> instanceHandle : container.listAll(typeLiteral)) {
            collection.add((E) instanceHandle.get());
        }
        return collection;
    }

    private static class QuarkusEventInitializer implements Initializer {

        private final Event<Object> event;

        public QuarkusEventInitializer(Event<Object> event) {
            this.event = event;
        }

        @Override
        public void initialize(JSqlClient sqlClient) {
            Triggers[] triggersArr = ((JSqlClientImplementor) sqlClient).getTriggerType() == TriggerType.BOTH
                    ? new Triggers[] { sqlClient.getTriggers(), sqlClient.getTriggers(true) }
                    : new Triggers[] { sqlClient.getTriggers() };
            for (Triggers triggers : triggersArr) {
                // Maybe there's a better way to handle this
                Event<EntityEvent<?>> eventEvent = event.select(new TypeLiteral<>() {
                });
                triggers.addEntityListener(eventEvent::fire);
                triggers.addAssociationListener(event::fire);
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
