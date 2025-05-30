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

import io.quarkiverse.jimmer.deployment.bytecode.JimmerRepositoryFactory;
import io.quarkiverse.jimmer.runtime.*;
import io.quarkiverse.jimmer.runtime.QuarkusSqlClientProducer;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
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
import io.quarkiverse.jimmer.runtime.repo.RepoRecord;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractKotlinRepository;
import io.quarkiverse.jimmer.runtime.repository.*;
import io.quarkiverse.jimmer.runtime.repository.support.JRepositoryImpl;
import io.quarkiverse.jimmer.runtime.repository.support.KRepositoryImpl;
import io.quarkiverse.jimmer.runtime.util.Constant;
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

    @BuildStep(onlyIf = IsJavaEnable.class)
    void indexJimmerForJava(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-core"));
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-sql"));
    }

    @BuildStep(onlyIf = IsKotlinEnable.class)
    void indexJimmerForKotlin(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-core-kotlin"));
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-sql-kotlin"));
    }

    @BuildStep
    IgnoreSplitPackageBuildItem splitPackages() {
        return new IgnoreSplitPackageBuildItem(List.of("org.babyfish.jimmer", "org.babyfish.jimmer.sql"));
    }

    @BuildStep
    AnnotationsTransformerBuildItem transform(CustomScopeAnnotationsBuildItem customScopes) {
        return new AnnotationsTransformerBuildItem(new TransientResolverTransformer(customScopes));
    }

    @BuildStep
    void setUpExceptionMapper(JimmerBuildTimeConfig buildTimeConfig,
            BuildProducer<ExceptionMapperBuildItem> exceptionMapperProducer) {
        if (buildTimeConfig.errorTranslator().isPresent()) {
            if (!buildTimeConfig.errorTranslator().get().disabled()) {
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
    void verifyConfig(@SuppressWarnings("unused") JimmerDataSourcesRecorder recorder, JimmerBuildTimeConfig buildTimeConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems) {
        if (!jdbcDataSourceBuildItems.isEmpty()) {
            for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
                String dataSourceName = jdbcDataSourceBuildItem.getName();
                if (buildTimeConfig.dataSources().get(dataSourceName).prettySql()
                        && !buildTimeConfig.dataSources().get(dataSourceName).showSql()) {
                    throw new IllegalArgumentException(
                            "When `pretty-sql` is true, `show-sql` must be true");
                }
                if (buildTimeConfig.dataSources().get(dataSourceName).inlineSqlVariables()
                        && !buildTimeConfig.dataSources().get(dataSourceName).prettySql()) {
                    throw new IllegalArgumentException(
                            "When `inline-sql-variables` is true, `pretty-sql` must be true");
                }
                if (buildTimeConfig.client().ts().path().isPresent()) {
                    if (!buildTimeConfig.client().ts().path().get().startsWith("/")) {
                        throw new IllegalArgumentException("`jimmer.client.ts.path` must start with \"/\"");
                    }
                }
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
            JimmerBuildTimeConfig buildTimeConfig,
            BuildProducer<RegistryBuildItem> registries) {
        if (buildTimeConfig.client().ts().path().isPresent()) {
            routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                    .management()
                    .routeFunction(buildTimeConfig.client().ts().path().get(), typeScriptRecorder.route())
                    .routeConfigKey("quarkus.jimmer.client.ts.path")
                    .handler(typeScriptRecorder.getHandler())
                    .blockingRoute()
                    .build());

            String tsPath = nonApplicationRootPathBuildItem.resolveManagementPath(buildTimeConfig.client().ts().path().get(),
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
                .routeFunction(buildTimeConfig.client().openapi().path(), openApiRecorder.route())
                .routeConfigKey("quarkus.jimmer.client.openapi.path")
                .handler(openApiRecorder.getHandler())
                .blockingRoute()
                .build());

        String openapiPath = nonApplicationRootPathBuildItem.resolveManagementPath(buildTimeConfig.client().openapi().path(),
                managementInterfaceBuildTimeConfig, launchModeBuildItem);
        log.debug("Initialized a Jimmer OpenApi meter registry on path = " + openapiPath);

        registries.produce(new RegistryBuildItem("OpenApiResource", openapiPath));

        routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                .management()
                .routeFunction(buildTimeConfig.client().openapi().uiPath(), openApiUiRecorder.route())
                .routeConfigKey("quarkus.jimmer.client.openapi.ui-path")
                .handler(openApiUiRecorder.getHandler())
                .blockingRoute()
                .build());

        String uiPath = nonApplicationRootPathBuildItem.resolveManagementPath(buildTimeConfig.client().openapi().uiPath(),
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

    @BuildStep
    void collectRepositoryMetadata(CombinedIndexBuildItem combinedIndex,
            BuildProducer<RepositoryMetadata> repositoryMetadataBuildProducer) {
        Collection<ClassInfo> jRepositoryInterfaces = combinedIndex.getIndex().getAllKnownSubinterfaces(JRepository.class);
        for (ClassInfo repositoryInterface : jRepositoryInterfaces) {
            Optional<AnnotationInstance> mapperDatasource = repositoryInterface.asClass().annotationsMap().entrySet().stream()
                    .filter(entry -> entry.getKey().equals(DotName.createSimple(DataSource.class)))
                    .map(Map.Entry::getValue)
                    .map(annotationList -> annotationList.get(0))
                    .findFirst();
            if (mapperDatasource.isPresent()) {
                String dataSourceName = mapperDatasource.get().value().asString();
                List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryInterface.name(),
                        DotName.createSimple(JRepository.class), combinedIndex.getIndex());
                repositoryMetadataBuildProducer
                        .produce(new RepositoryMetadata(JandexReflection.loadRawType(typeParameters.get(0)),
                                JandexReflection.loadClass(repositoryInterface), dataSourceName));
            } else {
                List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryInterface.name(),
                        DotName.createSimple(JRepository.class), combinedIndex.getIndex());
                repositoryMetadataBuildProducer
                        .produce(new RepositoryMetadata(JandexReflection.loadRawType(typeParameters.get(0)),
                                JandexReflection.loadClass(repositoryInterface), DataSourceUtil.DEFAULT_DATASOURCE_NAME));
            }
        }
        Collection<ClassInfo> kRepositoryInterfaces = combinedIndex.getIndex().getAllKnownSubinterfaces(KRepository.class);
        for (ClassInfo repositoryInterface : kRepositoryInterfaces) {
            Optional<AnnotationInstance> mapperDatasource = repositoryInterface.asClass().annotationsMap().entrySet().stream()
                    .filter(entry -> entry.getKey().equals(DotName.createSimple(DataSource.class)))
                    .map(Map.Entry::getValue)
                    .map(annotationList -> annotationList.get(0))
                    .findFirst();
            if (mapperDatasource.isPresent()) {
                String dataSourceName = mapperDatasource.get().value().asString();
                List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryInterface.name(),
                        DotName.createSimple(KRepository.class), combinedIndex.getIndex());
                repositoryMetadataBuildProducer
                        .produce(new RepositoryMetadata(JandexReflection.loadRawType(typeParameters.get(0)),
                                JandexReflection.loadClass(repositoryInterface), dataSourceName));
            } else {
                List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryInterface.name(),
                        DotName.createSimple(KRepository.class), combinedIndex.getIndex());
                repositoryMetadataBuildProducer
                        .produce(new RepositoryMetadata(JandexReflection.loadRawType(typeParameters.get(0)),
                                JandexReflection.loadClass(repositoryInterface), DataSourceUtil.DEFAULT_DATASOURCE_NAME));
            }
        }
    }

    @BuildStep(onlyIf = IsJavaEnable.class)
    @Record(ExecutionTime.STATIC_INIT)
    void analyzeJavaRepository(@SuppressWarnings("unused") RepoRecord repoRecord,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer,
            BuildProducer<EntityToClassBuildItem> entityToClassProducer) {
        Collection<ClassInfo> repositoryBeans = combinedIndex.getIndex()
                .getAllKnownSubclasses(AbstractJavaRepository.class);
        for (ClassInfo repositoryBean : repositoryBeans) {
            unremovableBeanProducer.produce(UnremovableBeanBuildItem.beanTypes(repositoryBean.name()));

            List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryBean.asClass().name(),
                    DotName.createSimple(AbstractJavaRepository.class), combinedIndex.getComputingIndex());
            entityToClassProducer.produce(new EntityToClassBuildItem(repositoryBean.asClass().name().toString(),
                    JandexReflection.loadRawType(typeParameters.get(0))));
        }
    }

    @BuildStep(onlyIf = IsKotlinEnable.class)
    @Record(ExecutionTime.STATIC_INIT)
    void analyzeKotlinRepository(@SuppressWarnings("unused") RepoRecord repoRecord,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer,
            BuildProducer<EntityToClassBuildItem> entityToClassProducer) {
        Collection<ClassInfo> repositoryBeans = combinedIndex.getIndex()
                .getAllKnownSubclasses(AbstractKotlinRepository.class);
        for (ClassInfo repositoryBean : repositoryBeans) {
            unremovableBeanProducer.produce(UnremovableBeanBuildItem.beanTypes(repositoryBean.name()));

            List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryBean.asClass().name(),
                    DotName.createSimple(AbstractKotlinRepository.class), combinedIndex.getComputingIndex());
            entityToClassProducer.produce(new EntityToClassBuildItem(repositoryBean.asClass().name().toString(),
                    JandexReflection.loadRawType(typeParameters.get(0))));
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void recordRepoOperationsData(RepoRecord repoRecord,
            List<EntityToClassBuildItem> entityToClassBuildItems) {
        Map<String, Class<?>> map = new HashMap<>();
        for (EntityToClassBuildItem entityToClassBuildItem : entityToClassBuildItems) {
            map.put(entityToClassBuildItem.getEntityClass(), entityToClassBuildItem.getClazz());
        }
        repoRecord.setEntityToClassUnit(map);
    }

    @BuildStep(onlyIf = IsJavaEnable.class)
    @Record(ExecutionTime.STATIC_INIT)
    void setTransactionJCacheOperatorBean(JimmerTransactionCacheOperatorRecorder recorder,
            JimmerBuildTimeConfig buildTimeConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        boolean transactionCacheOperatorFlusherFlag = jdbcDataSourceBuildItems.stream()
                .anyMatch(x -> !buildTimeConfig.dataSources().get(x.getName()).triggerType().equals(TriggerType.BINLOG_ONLY));
        if (transactionCacheOperatorFlusherFlag) {
            AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
            builder.addBeanClass(TransactionCacheOperatorFlusher.class);
            additionalBeans.produce(builder.build());
        }

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();
            if (!buildTimeConfig.dataSources().get(dataSourceName).triggerType().equals(TriggerType.BINLOG_ONLY)) {
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
    }

    @BuildStep(onlyIf = IsKotlinEnable.class)
    @Record(ExecutionTime.STATIC_INIT)
    void setTransactionKCacheOperatorBean(JimmerTransactionCacheOperatorRecorder recorder,
            JimmerBuildTimeConfig buildTimeConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        boolean transactionCacheOperatorFlusherFlag = jdbcDataSourceBuildItems.stream()
                .anyMatch(x -> !buildTimeConfig.dataSources().get(x.getName()).triggerType().equals(TriggerType.BINLOG_ONLY));
        if (transactionCacheOperatorFlusherFlag) {
            AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
            builder.addBeanClass(TransactionCacheOperatorFlusher.class);
            additionalBeans.produce(builder.build());
        }

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();
            if (!buildTimeConfig.dataSources().get(dataSourceName).triggerType().equals(TriggerType.BINLOG_ONLY)) {
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
                    .createWith(recorder.jSqlClientContainerFunction(dataSourceName));

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
                    .createWith(recorder.kSqlClientContainerFunction(dataSourceName));

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

    @BuildStep
    @Consume(RepositoryMetadata.class)
    void generateRepositoryImpl(List<RepositoryMetadata> repositoryBuildItems,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanBuildItem) {
        if (repositoryBuildItems.isEmpty()) {
            return;
        }
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeanBuildItem);
        for (RepositoryMetadata metadata : repositoryBuildItems) {
            JimmerRepositoryFactory jimmerRepositoryFactory = new JimmerRepositoryFactory(metadata);
            classOutput.write(jimmerRepositoryFactory.getTargetRepositoryClass().getName(),
                    jimmerRepositoryFactory.getTargetRepositoryBytes());
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
