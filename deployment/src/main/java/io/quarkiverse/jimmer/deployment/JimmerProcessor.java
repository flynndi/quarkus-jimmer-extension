package io.quarkiverse.jimmer.deployment;

import java.util.*;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.jimmer.runtime.DBKindEnum;
import io.quarkiverse.jimmer.runtime.JimmerDataSourcesRecorder;
import io.quarkiverse.jimmer.runtime.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.QuarkusJSqlClientProducer;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.SqlClientInitializer;
import io.quarkiverse.jimmer.runtime.cfg.TransactionCacheOperatorFlusherConfig;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptRecorder;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.runtime.DataSources;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
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
        if (!config.language.equals("java") && !config.language.equals("kotlin")) {
            throw new IllegalArgumentException("`jimmer.language` must be \"java\" or \"kotlin\"");
        }
        if (config.prettySql && !config.showSql) {
            throw new IllegalArgumentException(
                    "When `pretty-sql` is true, `show-sql` must be true");
        }
        if (config.inlineSqlVariables && !config.prettySql) {
            throw new IllegalArgumentException(
                    "When `inline-sql-variables` is true, `pretty-sql` must be true");
        }
        if (config.defaultBatchSize.isEmpty()) {
            config.defaultBatchSize = OptionalInt.of(JSqlClient.Builder.DEFAULT_BATCH_SIZE);
        }
        if (config.defaultListBatchSize.isEmpty()) {
            config.defaultListBatchSize = OptionalInt.of(JSqlClient.Builder.DEFAULT_LIST_BATCH_SIZE);
        }
        if (config.offsetOptimizingThreshold.isEmpty()) {
            config.offsetOptimizingThreshold = OptionalInt.of(Integer.MAX_VALUE);
        }
        if (config.errorTranslator.isEmpty()) {
            config.errorTranslator = Optional.of(new JimmerBuildTimeConfig.ErrorTranslator(null, null, null, null));
        }
        if (null == config.client) {
            config.client = new JimmerBuildTimeConfig.Client(null);
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void initializeResourceRegistry(TypeScriptRecorder recorder,
            BuildProducer<RouteBuildItem> routes,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig,
            LaunchModeBuildItem launchModeBuildItem,
            JimmerBuildTimeConfig config,
            BuildProducer<RegistryBuildItem> registries) {
        if (config.client.ts.path.isPresent()) {
            routes.produce(nonApplicationRootPathBuildItem.routeBuilder()
                    .management()
                    .routeFunction(config.client.ts.path.get(), recorder.route())
                    .routeConfigKey("quarkus.jimmer.client.ts.path")
                    .handler(recorder.getHandler())
                    .blockingRoute()
                    .build());

            String path = nonApplicationRootPathBuildItem.resolveManagementPath(config.client.ts.path.get(),
                    managementInterfaceBuildTimeConfig, launchModeBuildItem);
            log.debug("Initialized a Jimmer TypeScript meter registry on path = " + path);

            registries.produce(new RegistryBuildItem("TypeScriptResource", path));
        }
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
        if (buildItem.getMap().containsKey(DotName.createSimple(TransactionCacheOperator.class))) {
            AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();
            builder.addBeanClass(TransactionCacheOperatorFlusherConfig.class);
            builder.addBeanClass(TransactionCacheOperatorFlusher.class);
            additionalBeans.produce(builder.build());
        }
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
                        if (method.returnType().name().equals(DotName.createSimple(TransactionCacheOperator.class))) {
                            map.put(DotName.createSimple(TransactionCacheOperator.class), Boolean.TRUE);
                        }
                    }
                }
            }
        }
    }
}
