package io.quarkiverse.jimmer.deployment;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Priorities;

import org.babyfish.jimmer.error.CodeBasedException;
import org.babyfish.jimmer.error.CodeBasedRuntimeException;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.jimmer.runtime.*;
import io.quarkiverse.jimmer.runtime.cache.impl.QuarkusTransactionCacheOperator;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.SqlClientInitializer;
import io.quarkiverse.jimmer.runtime.cfg.TransactionCacheOperatorFlusherConfig;
import io.quarkiverse.jimmer.runtime.client.CodeBasedExceptionAdvice;
import io.quarkiverse.jimmer.runtime.client.CodeBasedRuntimeExceptionAdvice;
import io.quarkiverse.jimmer.runtime.client.openapi.CssRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.JsRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiUiRecorder;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptRecorder;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.runtime.DataSources;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;

public class JimmerProcessor {

    private static final Logger log = Logger.getLogger(JimmerProcessor.class);

    private static final String FEATURE = "jimmer";

    private static final String JIMMER_CONTAINER_BEAN_NAME_PREFIX = "jimmer_container_";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void verifyConfig(JimmerDataSourcesRecorder recorder, JimmerBuildTimeConfig config) {
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

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerRepository(JimmerJpaRecorder jimmerJpaRecorder,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer,
            BuildProducer<EntityToImmutableTypeBuildItem> entityToImmutableTypeProducer) {
        Collection<ClassInfo> repositoryBeans = combinedIndex.getComputingIndex().getAllKnownImplementors(JRepository.class);
        for (ClassInfo repositoryBean : repositoryBeans) {
            unremovableBeanProducer.produce(UnremovableBeanBuildItem.beanTypes(repositoryBean.name()));

            List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryBean.asClass().name(),
                    DotName.createSimple(JRepository.class), combinedIndex.getComputingIndex());
            entityToImmutableTypeProducer.produce(new EntityToImmutableTypeBuildItem(typeParameters.get(0).name().toString(),
                    ImmutableType.get(JandexReflection.loadRawType(typeParameters.get(0)))));
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void recordJpaOperationsData(JimmerJpaRecorder jimmerJpaRecorder,
            List<EntityToImmutableTypeBuildItem> entityToImmutableTypes) {
        for (EntityToImmutableTypeBuildItem entityToImmutableType : entityToImmutableTypes) {
            System.out.println("entityToImmutableType = " + entityToImmutableType);
        }
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

    @BuildStep
    @Produce(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(LoggingSetupBuildItem.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateJSqlClientBeans(JimmerDataSourcesRecorder recorder,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        additionalBeans.produce(new AdditionalBeanBuildItem(JSqlClient.class));

        additionalBeans
                .produce(AdditionalBeanBuildItem.builder().addBeanClasses(QuarkusJSqlClientProducer.class).setUnremovable()
                        .setDefaultScope(DotNames.SINGLETON).build());

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator quarkusJSqlClientContainerConfigurator = SyntheticBeanBuildItem
                    .configure(QuarkusJSqlClientContainer.class)
                    .scope(Singleton.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(DotName.createSimple(QuarkusJSqlClientProducer.class)))
                    .addInjectionPoint(ClassType.create(DotName.createSimple(DataSources.class)))
                    .createWith(recorder.sqlClientContainerFunction(dataSourceName,
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
        }
    }

    @BuildStep
    @Produce(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(LoggingSetupBuildItem.class)
    void sqlClientInitializer(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(SqlClientInitializer.class));
    }

    @BuildStep
    void registerBeanProducers(CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        JimmerBeanNameToDotNameBuildItem buildItem = collectBuildItem(combinedIndex);
        if (buildItem.getMap().containsKey(DotName.createSimple(QuarkusTransactionCacheOperator.class))) {
            AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
            builder.addBeanClass(TransactionCacheOperatorFlusherConfig.class);
            builder.addBeanClass(TransactionCacheOperatorFlusher.class);
            additionalBeans.produce(builder.build());
        }
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
    void registerNativeImageResources(BuildProducer<NativeImageResourceBuildItem> resource) {
        resource.produce(new NativeImageResourceBuildItem(
                Constant.TEMPLATE_RESOURCE,
                Constant.NO_API_RESOURCE,
                Constant.NO_METADATA_RESOURCE,
                Constant.CLIENT_RESOURCE,
                Constant.ENTITIES_RESOURCE,
                Constant.IMMUTABLES_RESOURCE));
    }

    private JimmerBeanNameToDotNameBuildItem collectBuildItem(CombinedIndexBuildItem combinedIndex) {
        Map<DotName, Boolean> map = new HashMap<>();
        Collection<AnnotationInstance> singletonInstances = combinedIndex.getIndex().getAnnotations(DotNames.SINGLETON);
        Collection<AnnotationInstance> applicationScopedInstances = combinedIndex.getIndex()
                .getAnnotations(DotNames.APPLICATION_SCOPED);
        generateBuildItem(map, singletonInstances);
        generateBuildItem(map, applicationScopedInstances);
        return new JimmerBeanNameToDotNameBuildItem(map);
    }

    private void generateBuildItem(Map<DotName, Boolean> map, Collection<AnnotationInstance> instances) {
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind().equals(AnnotationTarget.Kind.METHOD)) {
                MethodInfo method = instance.target().asMethod();
                ClassInfo classInfo = method.declaringClass();
                if (classInfo.hasDeclaredAnnotation(DotNames.APPLICATION_SCOPED)
                        || classInfo.hasDeclaredAnnotation(DotNames.SINGLETON)) {
                    if (method.hasDeclaredAnnotation(DotNames.SINGLETON)
                            || method.hasDeclaredAnnotation(DotNames.APPLICATION_SCOPED)) {
                        if (method.returnType().name().equals(DotName.createSimple(QuarkusTransactionCacheOperator.class))) {
                            map.put(DotName.createSimple(QuarkusTransactionCacheOperator.class), Boolean.TRUE);
                        }
                    }
                }
            }
        }
    }
}
