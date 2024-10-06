package io.quarkiverse.jimmer.deployment;

import java.util.*;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Priorities;

import org.babyfish.jimmer.error.CodeBasedException;
import org.babyfish.jimmer.error.CodeBasedRuntimeException;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.TriggerType;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.jimmer.runtime.*;
import io.quarkiverse.jimmer.runtime.QuarkusSqlClientProducer;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.SqlClientInitializer;
import io.quarkiverse.jimmer.runtime.client.CodeBasedExceptionAdvice;
import io.quarkiverse.jimmer.runtime.client.CodeBasedRuntimeExceptionAdvice;
import io.quarkiverse.jimmer.runtime.client.openapi.CssRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.JsRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiUiRecorder;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptRecorder;
import io.quarkiverse.jimmer.runtime.cloud.ExchangeRestClient;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterAssociatedIdsRecorder;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsRecorder;
import io.quarkiverse.jimmer.runtime.cloud.QuarkusExchange;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.repository.*;
import io.quarkiverse.jimmer.runtime.repository.support.JRepositoryImpl;
import io.quarkiverse.jimmer.runtime.repository.support.KRepositoryImpl;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkiverse.jimmer.runtime.util.DBKindEnum;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.runtime.DataSources;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.*;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;

@BuildSteps(onlyIf = JimmerProcessor.JimmerEnable.class)
final class JimmerProcessor {

    private static final Logger log = Logger.getLogger(JimmerProcessor.class);

    private static final String FEATURE = "jimmer";

    private static final String JIMMER_CONTAINER_BEAN_NAME_PREFIX = "jimmer_container_";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexJimmer(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-core"));
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-sql"));
    }

    @BuildStep
    AnnotationsTransformerBuildItem transform(CustomScopeAnnotationsBuildItem customScopes) {
        return new AnnotationsTransformerBuildItem(new TransientResolverTransformer(customScopes));
    }

    @BuildStep
    void setUpExceptionMapper(JimmerBuildTimeConfig config, BuildProducer<ExceptionMapperBuildItem> exceptionMapperProducer) {
        if (config.errorTranslator().isPresent()) {
            if (!config.errorTranslator().get().disabled()) {
                exceptionMapperProducer.produce(new ExceptionMapperBuildItem(CodeBasedExceptionAdvice.class.getName(),
                        CodeBasedException.class.getName(), Priorities.USER + 1, true));
                exceptionMapperProducer.produce(new ExceptionMapperBuildItem(CodeBasedRuntimeExceptionAdvice.class.getName(),
                        CodeBasedRuntimeException.class.getName(), Priorities.USER + 1, true));
            }
        }
    }

    @BuildStep
    void checkTransactionsSupport(Capabilities capabilities,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> validationErrors) {
        // JTA is necessary for Jimmer
        if (capabilities.isMissing(Capability.TRANSACTIONS)) {
            validationErrors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                    new ConfigurationException("The Jimmer extension is only functional in a JTA environment.")));
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void verifyConfig(@SuppressWarnings("unused") JimmerDataSourcesRecorder recorder, JimmerBuildTimeConfig config) {
        if (!config.language().equals("java") && !config.language().equals("kotlin")) {
            throw new IllegalArgumentException("`jimmer.language` must be \"java\" or \"kotlin\"");
        }
        if (config.prettySql() && !config.showSql()) {
            throw new IllegalArgumentException(
                    "When `pretty-sql` is true, `show-sql` must be true");
        }
        if (config.inlineSqlVariables() && !config.prettySql()) {
            throw new IllegalArgumentException(
                    "When `inline-sql-variables` is true, `pretty-sql` must be true");
        }
        if (config.client().ts().path().isPresent()) {
            if (!config.client().ts().path().get().startsWith("/")) {
                throw new IllegalArgumentException("`jimmer.client.ts.path` must start with \"/\"");
            }
        }
    }

    @BuildStep(onlyIf = IsMicroServiceEnable.class)
    @Record(ExecutionTime.STATIC_INIT)
    void setUpMicroService(BuildProducer<RouteBuildItem> routes,
            LaunchModeBuildItem launchModeBuildItem,
            BuildProducer<RegistryBuildItem> registries,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            MicroServiceExporterIdsRecorder microServiceExporterIdsRecorder,
            MicroServiceExporterAssociatedIdsRecorder microServiceExporterAssociatedIdsRecorder,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig,
            BuildProducer<AdditionalIndexedClassesBuildItem> additionalIndexedClassesBuildItem) {

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .management()
                .routeFunction(Constant.BY_IDS, microServiceExporterIdsRecorder.route())
                .handler(microServiceExporterIdsRecorder.getHandler())
                .blockingRoute()
                .build());

        String microServiceExporterIdsPath = nonApplicationRootPathBuildItem.resolveManagementPath(
                Constant.BY_IDS,
                managementInterfaceBuildTimeConfig, launchModeBuildItem);
        log.debug(
                "Initialized a Jimmer microServiceExporterIdsPath meter registry on path = " + microServiceExporterIdsPath);

        registries.produce(new RegistryBuildItem("microServiceExporterIdsPath", microServiceExporterIdsPath));

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .management()
                .routeFunction(Constant.BY_ASSOCIATED_IDS, microServiceExporterAssociatedIdsRecorder.route())
                .handler(microServiceExporterAssociatedIdsRecorder.getHandler())
                .blockingRoute()
                .build());

        String microServiceExporterAssociatedIdsPath = nonApplicationRootPathBuildItem.resolveManagementPath(
                Constant.BY_ASSOCIATED_IDS,
                managementInterfaceBuildTimeConfig, launchModeBuildItem);
        log.debug("Initialized a Jimmer microServiceExporterAssociatedPath meter registry on path = "
                + microServiceExporterAssociatedIdsPath);

        registries.produce(
                new RegistryBuildItem("microServiceExporterAssociatedPath", microServiceExporterAssociatedIdsPath));

        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(QuarkusExchange.class));

        additionalIndexedClassesBuildItem
                .produce(new AdditionalIndexedClassesBuildItem(ExchangeRestClient.class.getName()));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void initializeResourceRegistry(TypeScriptRecorder typeScriptRecorder,
            CssRecorder cssRecorder,
            JsRecorder jsRecorder,
            OpenApiRecorder openApiRecorder,
            OpenApiUiRecorder openApiUiRecorder,
            BuildProducer<RouteBuildItem> routes,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig,
            LaunchModeBuildItem launchModeBuildItem,
            JimmerBuildTimeConfig config,
            BuildProducer<RegistryBuildItem> registries) {
        if (config.client().ts().path().isPresent()) {
            routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                    .management()
                    .routeFunction(config.client().ts().path().get(), typeScriptRecorder.route())
                    .routeConfigKey("quarkus.jimmer.client.ts.path")
                    .handler(typeScriptRecorder.getHandler())
                    .blockingRoute()
                    .build());

            String tsPath = nonApplicationRootPathBuildItem.resolveManagementPath(config.client().ts().path().get(),
                    managementInterfaceBuildTimeConfig, launchModeBuildItem);
            log.debug("Initialized a Jimmer TypeScript meter registry on path = " + tsPath);

            registries.produce(new RegistryBuildItem("TypeScriptResource", tsPath));
        }

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .management()
                .routeFunction(Constant.CSS_URL, cssRecorder.route())
                .handler(cssRecorder.getHandler())
                .blockingRoute()
                .build());

        String cssPath = nonApplicationRootPathBuildItem.resolveManagementPath(Constant.CSS_URL,
                managementInterfaceBuildTimeConfig, launchModeBuildItem);
        log.debug("Initialized a Jimmer CSS meter registry on path = " + cssPath);

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .management()
                .routeFunction(Constant.JS_URL, jsRecorder.route())
                .handler(jsRecorder.getHandler())
                .blockingRoute()
                .build());

        String jsPath = nonApplicationRootPathBuildItem.resolveManagementPath(Constant.JS_URL,
                managementInterfaceBuildTimeConfig, launchModeBuildItem);
        log.debug("Initialized a Jimmer JS meter registry on path = " + jsPath);

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .management()
                .routeFunction(config.client().openapi().path(), openApiRecorder.route())
                .routeConfigKey("quarkus.jimmer.client.openapi.path")
                .handler(openApiRecorder.getHandler())
                .blockingRoute()
                .build());

        String openapiPath = nonApplicationRootPathBuildItem.resolveManagementPath(config.client().openapi().path(),
                managementInterfaceBuildTimeConfig, launchModeBuildItem);
        log.debug("Initialized a Jimmer OpenApi meter registry on path = " + openapiPath);

        registries.produce(new RegistryBuildItem("OpenApiResource", openapiPath));

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .management()
                .routeFunction(config.client().openapi().uiPath(), openApiUiRecorder.route())
                .routeConfigKey("quarkus.jimmer.client.openapi.ui-path")
                .handler(openApiUiRecorder.getHandler())
                .blockingRoute()
                .build());

        String uiPath = nonApplicationRootPathBuildItem.resolveManagementPath(config.client().openapi().uiPath(),
                managementInterfaceBuildTimeConfig, launchModeBuildItem);
        log.debug("Initialized a Jimmer OpenApiUi meter registry on path = " + uiPath);

        registries.produce(new RegistryBuildItem("OpenApiUiResource", uiPath));
    }

    @BuildStep(onlyIf = IsJavaEnable.class)
    void contributeJRepositoryToIndex(BuildProducer<AdditionalIndexedClassesBuildItem> additionalIndexedClasses) {
        additionalIndexedClasses
                .produce(new AdditionalIndexedClassesBuildItem(JRepository.class.getName(), JRepositoryImpl.class.getName()));
    }

    @BuildStep(onlyIf = IsKotlinEnable.class)
    void contributeKRepositoryToIndex(BuildProducer<AdditionalIndexedClassesBuildItem> additionalIndexedClasses) {
        additionalIndexedClasses
                .produce(new AdditionalIndexedClassesBuildItem(KRepository.class.getName(), KRepositoryImpl.class.getName()));
    }

    @BuildStep(onlyIf = IsJavaEnable.class)
    @Record(ExecutionTime.STATIC_INIT)
    void collectJRepositoryInfo(@SuppressWarnings("unused") JimmerJpaRecorder jimmerJpaRecorder,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<RepositoryBuildItem> repositoryBuildProducer) {
        Collection<ClassInfo> repositoryInterfaces = combinedIndex.getIndex().getAllKnownSubinterfaces(JRepository.class);
        for (ClassInfo repositoryInterface : repositoryInterfaces) {
            DotName dotName = repositoryInterface.asClass().name();
            Optional<AnnotationInstance> mapperDatasource = repositoryInterface.asClass().annotationsMap().entrySet().stream()
                    .filter(entry -> entry.getKey().equals(DotName.createSimple(DataSource.class)))
                    .map(Map.Entry::getValue)
                    .map(annotationList -> annotationList.get(0))
                    .findFirst();

            DotName entityDotName = null;
            DotName idDotName = null;
            for (DotName extendedRepo : repositoryInterface.interfaceNames()) {
                List<Type> types = JandexUtil.resolveTypeParameters(repositoryInterface.name(), extendedRepo,
                        combinedIndex.getIndex());
                if (!(types.get(0) instanceof ClassType)) {
                    throw new IllegalArgumentException(
                            "Entity generic argument of " + repositoryInterface + " is not a regular class type");
                }
                DotName newEntityDotName = types.get(0).name();
                if ((entityDotName != null) && !newEntityDotName.equals(entityDotName)) {
                    throw new IllegalArgumentException(
                            "Repository " + repositoryInterface + " specifies multiple Entity types");
                }
                entityDotName = newEntityDotName;

                DotName newIdDotName = types.get(1).name();
                if ((idDotName != null) && !newIdDotName.equals(idDotName)) {
                    throw new IllegalArgumentException("Repository " + repositoryInterface + " specifies multiple ID types");
                }
                idDotName = newIdDotName;
            }

            if (idDotName == null || entityDotName == null) {
                throw new IllegalArgumentException(
                        "Repository " + repositoryInterface + " does not specify ID and/or Entity type");
            }
            if (mapperDatasource.isPresent()) {
                String dataSourceName = mapperDatasource.get().value().asString();
                repositoryBuildProducer.produce(new RepositoryBuildItem(dotName, dataSourceName,
                        new AbstractMap.SimpleEntry<>(idDotName, entityDotName)));
            } else {
                repositoryBuildProducer.produce(
                        new RepositoryBuildItem(dotName, "<default>", new AbstractMap.SimpleEntry<>(idDotName, entityDotName)));
            }
        }
    }

    @BuildStep(onlyIf = IsKotlinEnable.class)
    @Record(ExecutionTime.STATIC_INIT)
    void collectKRepositoryInfo(@SuppressWarnings("unused") JimmerJpaRecorder jimmerJpaRecorder,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<RepositoryBuildItem> repositoryBuildProducer) {
        Collection<ClassInfo> repositoryInterfaces = combinedIndex.getIndex().getAllKnownSubinterfaces(KRepository.class);
        for (ClassInfo repositoryInterface : repositoryInterfaces) {
            DotName dotName = repositoryInterface.asClass().name();
            Optional<AnnotationInstance> mapperDatasource = repositoryInterface.asClass().annotationsMap().entrySet().stream()
                    .filter(entry -> entry.getKey().equals(DotName.createSimple(DataSource.class)))
                    .map(Map.Entry::getValue)
                    .map(annotationList -> annotationList.get(0))
                    .findFirst();

            DotName entityDotName = null;
            DotName idDotName = null;
            for (DotName extendedRepo : repositoryInterface.interfaceNames()) {
                List<Type> types = JandexUtil.resolveTypeParameters(repositoryInterface.name(), extendedRepo,
                        combinedIndex.getIndex());
                if (!(types.get(0) instanceof ClassType)) {
                    throw new IllegalArgumentException(
                            "Entity generic argument of " + repositoryInterface + " is not a regular class type");
                }
                DotName newEntityDotName = types.get(0).name();
                if ((entityDotName != null) && !newEntityDotName.equals(entityDotName)) {
                    throw new IllegalArgumentException(
                            "Repository " + repositoryInterface + " specifies multiple Entity types");
                }
                entityDotName = newEntityDotName;

                DotName newIdDotName = types.get(1).name();
                if ((idDotName != null) && !newIdDotName.equals(idDotName)) {
                    throw new IllegalArgumentException("Repository " + repositoryInterface + " specifies multiple ID types");
                }
                idDotName = newIdDotName;
            }

            if (idDotName == null || entityDotName == null) {
                throw new IllegalArgumentException(
                        "Repository " + repositoryInterface + " does not specify ID and/or Entity type");
            }
            if (mapperDatasource.isPresent()) {
                String dataSourceName = mapperDatasource.get().value().asString();
                repositoryBuildProducer.produce(new RepositoryBuildItem(dotName, dataSourceName,
                        new AbstractMap.SimpleEntry<>(idDotName, entityDotName)));
            } else {
                repositoryBuildProducer.produce(
                        new RepositoryBuildItem(dotName, "<default>", new AbstractMap.SimpleEntry<>(idDotName, entityDotName)));
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void recordJpaOperationsData(JimmerJpaRecorder jimmerJpaRecorder,
            List<EntityToClassBuildItem> entityToClassBuildItems) {
        Map<String, Class<?>> map = new HashMap<>();
        for (EntityToClassBuildItem entityToClassBuildItem : entityToClassBuildItems) {
            map.put(entityToClassBuildItem.getEntityClass(), entityToClassBuildItem.getClazz());
        }
        jimmerJpaRecorder.setEntityToClassUnit(map);
    }

    @BuildStep(onlyIf = { IsJavaEnable.class, IsTransactionOnlyEnable.class })
    @Record(ExecutionTime.STATIC_INIT)
    void setTransactionJCacheOperatorBean(JimmerTransactionCacheOperatorRecorder recorder,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
        builder.addBeanClass(TransactionCacheOperatorFlusher.class);
        additionalBeans.produce(builder.build());

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator transactionCacheOperatorConfigurator = SyntheticBeanBuildItem
                    .configure(TransactionCacheOperator.class)
                    .scope(Singleton.class)
                    .unremovable()
                    .addInjectionPoint(ClassType.create(DotName.createSimple(DataSources.class)))
                    .createWith(recorder.transactionJCacheOperatorFunction(dataSourceName));

            if (DataSourceUtil.isDefault(dataSourceName)) {
                transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                        .addValue("value", dataSourceName).done();

                transactionCacheOperatorConfigurator.priority(10);

            } else {
                transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                        .addValue("value", dataSourceName).done();

                transactionCacheOperatorConfigurator.priority(5);
            }

            syntheticBeanBuildItemBuildProducer.produce(transactionCacheOperatorConfigurator.done());
        }
    }

    @BuildStep(onlyIf = { IsKotlinEnable.class, IsTransactionOnlyEnable.class })
    @Record(ExecutionTime.STATIC_INIT)
    void setTransactionKCacheOperatorBean(JimmerTransactionCacheOperatorRecorder recorder,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
        builder.addBeanClass(TransactionCacheOperatorFlusher.class);
        additionalBeans.produce(builder.build());

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator transactionCacheOperatorConfigurator = SyntheticBeanBuildItem
                    .configure(TransactionCacheOperator.class)
                    .scope(Singleton.class)
                    .unremovable()
                    .addInjectionPoint(ClassType.create(DotName.createSimple(DataSources.class)))
                    .createWith(recorder.transactionKCacheOperatorFunction(dataSourceName));

            if (DataSourceUtil.isDefault(dataSourceName)) {
                transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                        .addValue("value", dataSourceName).done();

                transactionCacheOperatorConfigurator.priority(10);

            } else {
                transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                        .addValue("value", dataSourceName).done();

                transactionCacheOperatorConfigurator.priority(5);
            }

            syntheticBeanBuildItemBuildProducer.produce(transactionCacheOperatorConfigurator.done());
        }
    }

    @BuildStep
    @Produce(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(LoggingSetupBuildItem.class)
    void sqlClientInitializer(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(SqlClientInitializer.class));
    }

    @BuildStep(onlyIf = IsJavaEnable.class)
    @Produce(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(LoggingSetupBuildItem.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateJSqlClientBeans(JimmerDataSourcesRecorder recorder,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<SqlClientBuildItem> sqlClientBuildItemBuildItem) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        additionalBeans.produce(new AdditionalBeanBuildItem(JSqlClient.class));

        additionalBeans
                .produce(AdditionalBeanBuildItem.builder().addBeanClasses(QuarkusSqlClientProducer.class).setUnremovable()
                        .setDefaultScope(DotNames.SINGLETON).build());

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator quarkusJSqlClientContainerConfigurator = SyntheticBeanBuildItem
                    .configure(QuarkusJSqlClientContainer.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(DotName.createSimple(QuarkusSqlClientProducer.class)))
                    .addInjectionPoint(ClassType.create(DotName.createSimple(DataSources.class)))
                    .createWith(recorder.jSqlClientContainerFunction(dataSourceName,
                            DBKindEnum.selectDialect(jdbcDataSourceBuildItem.getDbKind())));

            AnnotationInstance quarkusJSqlClientContainerQualifier;

            if (DataSourceUtil.isDefault(dataSourceName)) {
                quarkusJSqlClientContainerConfigurator.addQualifier(Default.class);

                quarkusJSqlClientContainerConfigurator.priority(10);

                quarkusJSqlClientContainerQualifier = AnnotationInstance.builder(Default.class).build();
            } else {
                String beanName = JIMMER_CONTAINER_BEAN_NAME_PREFIX + dataSourceName;
                quarkusJSqlClientContainerConfigurator.name(beanName);

                quarkusJSqlClientContainerConfigurator.addQualifier().annotation(DotNames.NAMED).addValue("value", beanName)
                        .done();
                quarkusJSqlClientContainerConfigurator.addQualifier().annotation(DataSource.class)
                        .addValue("value", dataSourceName).done();
                quarkusJSqlClientContainerConfigurator.priority(5);

                quarkusJSqlClientContainerQualifier = AnnotationInstance.builder(DataSource.class).add("value", dataSourceName)
                        .build();
            }

            syntheticBeanBuildItemBuildProducer.produce(quarkusJSqlClientContainerConfigurator.done());

            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(JSqlClient.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(DotName.createSimple(QuarkusJSqlClientContainer.class)),
                            quarkusJSqlClientContainerQualifier)
                    .createWith(recorder.quarkusJSqlClientFunction(dataSourceName));

            if (DataSourceUtil.isDefault(dataSourceName)) {
                configurator.addQualifier(Default.class);
                configurator.priority(10);
            } else {
                String beanName = FEATURE + "_" + dataSourceName;
                configurator.name(beanName);
                configurator.priority(5);

                configurator.addQualifier().annotation(DotNames.NAMED).addValue("value", beanName).done();
                configurator.addQualifier().annotation(DataSource.class).addValue("value", dataSourceName).done();
            }

            syntheticBeanBuildItemBuildProducer.produce(configurator.done());

            sqlClientBuildItemBuildItem.produce(new SqlClientBuildItem(dataSourceName));
        }
    }

    @BuildStep(onlyIf = IsKotlinEnable.class)
    @Produce(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(LoggingSetupBuildItem.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateKSqlClientBeans(JimmerDataSourcesRecorder recorder,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<SqlClientBuildItem> sqlClientBuildItemBuildItem) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        additionalBeans.produce(new AdditionalBeanBuildItem(KSqlClient.class));

        additionalBeans
                .produce(AdditionalBeanBuildItem.builder().addBeanClasses(QuarkusSqlClientProducer.class).setUnremovable()
                        .setDefaultScope(DotNames.SINGLETON).build());

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator quarkusKSqlClientContainerConfigurator = SyntheticBeanBuildItem
                    .configure(QuarkusKSqlClientContainer.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(DotName.createSimple(QuarkusSqlClientProducer.class)))
                    .addInjectionPoint(ClassType.create(DotName.createSimple(DataSources.class)))
                    .createWith(recorder.kSqlClientContainerFunction(dataSourceName,
                            DBKindEnum.selectDialect(jdbcDataSourceBuildItem.getDbKind())));

            AnnotationInstance quarkusKSqlClientContainerQualifier;

            if (DataSourceUtil.isDefault(dataSourceName)) {
                quarkusKSqlClientContainerConfigurator.addQualifier(Default.class);

                quarkusKSqlClientContainerConfigurator.priority(10);

                quarkusKSqlClientContainerQualifier = AnnotationInstance.builder(Default.class).build();
            } else {
                String beanName = JIMMER_CONTAINER_BEAN_NAME_PREFIX + dataSourceName;
                quarkusKSqlClientContainerConfigurator.name(beanName);

                quarkusKSqlClientContainerConfigurator.addQualifier().annotation(DotNames.NAMED).addValue("value", beanName)
                        .done();
                quarkusKSqlClientContainerConfigurator.addQualifier().annotation(DataSource.class)
                        .addValue("value", dataSourceName).done();
                quarkusKSqlClientContainerConfigurator.priority(5);

                quarkusKSqlClientContainerQualifier = AnnotationInstance.builder(DataSource.class).add("value", dataSourceName)
                        .build();
            }

            syntheticBeanBuildItemBuildProducer.produce(quarkusKSqlClientContainerConfigurator.done());

            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(KSqlClient.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(DotName.createSimple(QuarkusKSqlClientContainer.class)),
                            quarkusKSqlClientContainerQualifier)
                    .createWith(recorder.quarkusKSqlClientFunction(dataSourceName));

            if (DataSourceUtil.isDefault(dataSourceName)) {
                configurator.addQualifier(Default.class);
                configurator.priority(10);
            } else {
                String beanName = FEATURE + "_" + dataSourceName;
                configurator.name(beanName);
                configurator.priority(5);

                configurator.addQualifier().annotation(DotNames.NAMED).addValue("value", beanName).done();
                configurator.addQualifier().annotation(DataSource.class).addValue("value", dataSourceName).done();
            }

            syntheticBeanBuildItemBuildProducer.produce(configurator.done());

            sqlClientBuildItemBuildItem.produce(new SqlClientBuildItem(dataSourceName));
        }
    }

    @BuildStep(onlyIf = IsJavaEnable.class)
    @Consume(SqlClientBuildItem.class)
    void generateJRepository(CombinedIndexBuildItem combinedIndex, BuildProducer<GeneratedBeanBuildItem> generatedBeanBuildItem,
            List<RepositoryBuildItem> repositoryBuildItems) {
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeanBuildItem);
        ClassInfo jRepositoryClassInfo = combinedIndex.getIndex().getClassByName(JRepository.class);
        List<MethodInfo> methodInfos = jRepositoryClassInfo.methods();
        for (RepositoryBuildItem repositoryBuildItem : repositoryBuildItems) {
            log.trace("Ready to generate the implementation class");
            RepositoryCreator repositoryCreator = new RepositoryCreator(classOutput, methodInfos,
                    repositoryBuildItem.getRepositoryName(), repositoryBuildItem.getDataSourceName(),
                    repositoryBuildItem.getDotIdDotNameEntry());
            RepositoryCreator.Result result = repositoryCreator.implementCrudJRepository();
            log.tracev("Generation implementation class: {0}, entity: {1}, idType: {2}", result.getGeneratedClassName(),
                    result.getEntityDotName(), result.getIdTypeDotName());
        }
    }

    @BuildStep(onlyIf = IsKotlinEnable.class)
    @Consume(SqlClientBuildItem.class)
    void generateKRepository(CombinedIndexBuildItem combinedIndex, BuildProducer<GeneratedBeanBuildItem> generatedBeanBuildItem,
            List<RepositoryBuildItem> repositoryBuildItems) {
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeanBuildItem);
        ClassInfo kRepositoryClassInfo = combinedIndex.getIndex().getClassByName(KRepository.class);
        List<MethodInfo> methodInfos = kRepositoryClassInfo.methods();
        for (RepositoryBuildItem repositoryBuildItem : repositoryBuildItems) {
            log.trace("Ready to generate the implementation class");
            RepositoryCreator repositoryCreator = new RepositoryCreator(classOutput, methodInfos,
                    repositoryBuildItem.getRepositoryName(), repositoryBuildItem.getDataSourceName(),
                    repositoryBuildItem.getDotIdDotNameEntry());
            RepositoryCreator.Result result = repositoryCreator.implementCrudKRepository();
            log.tracev("Generation implementation class: {0}, entity: {1}, idType: {2}", result.getGeneratedClassName(),
                    result.getEntityDotName(), result.getIdTypeDotName());
        }
    }

    @BuildStep
    void registerNativeImageResources(BuildProducer<NativeImageResourceBuildItem> resource) {
        resource.produce(new NativeImageResourceBuildItem(
                Constant.TEMPLATE_RESOURCE,
                Constant.NO_API_RESOURCE,
                Constant.NO_METADATA_RESOURCE,
                Constant.CLIENT_RESOURCE,
                Constant.ENTITIES_RESOURCE,
                Constant.IMMUTABLES_RESOURCE));
    }

    static final class JimmerEnable extends AbstractJimmerBooleanSupplier {
        private JimmerEnable(JimmerBuildTimeConfig jimmerBuildTimeConfig) {
            super(jimmerBuildTimeConfig);
        }

        @Override
        public boolean getAsBoolean() {
            return jimmerBuildTimeConfig.enable();
        }
    }

    static final class IsMicroServiceEnable extends AbstractJimmerBooleanSupplier {

        private IsMicroServiceEnable(JimmerBuildTimeConfig jimmerBuildTimeConfig) {
            super(jimmerBuildTimeConfig);
        }

        @Override
        public boolean getAsBoolean() {
            return jimmerBuildTimeConfig.microServiceName().isPresent()
                    && !jimmerBuildTimeConfig.microServiceName().get().isEmpty();
        }
    }

    static final class IsTransactionOnlyEnable extends AbstractJimmerBooleanSupplier {

        private IsTransactionOnlyEnable(JimmerBuildTimeConfig jimmerBuildTimeConfig) {
            super(jimmerBuildTimeConfig);
        }

        @Override
        public boolean getAsBoolean() {
            return jimmerBuildTimeConfig.triggerType().equals(TriggerType.TRANSACTION_ONLY)
                    || jimmerBuildTimeConfig.triggerType().equals(TriggerType.BOTH);
        }
    }

    static final class IsKotlinEnable extends AbstractJimmerBooleanSupplier {

        private IsKotlinEnable(JimmerBuildTimeConfig jimmerBuildTimeConfig) {
            super(jimmerBuildTimeConfig);
        }

        @Override
        public boolean getAsBoolean() {
            return jimmerBuildTimeConfig.language().equalsIgnoreCase("kotlin");
        }
    }

    static final class IsJavaEnable extends AbstractJimmerBooleanSupplier {

        private IsJavaEnable(JimmerBuildTimeConfig jimmerBuildTimeConfig) {
            super(jimmerBuildTimeConfig);
        }

        @Override
        public boolean getAsBoolean() {
            return jimmerBuildTimeConfig.language().equalsIgnoreCase("java");
        }
    }
}
