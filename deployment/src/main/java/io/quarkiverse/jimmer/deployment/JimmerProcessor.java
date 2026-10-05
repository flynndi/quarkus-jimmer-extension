package io.quarkiverse.jimmer.deployment;

import java.beans.Introspector;
import java.util.List;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Named;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.JavaEnabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.KotlinEnabled;
import io.quarkiverse.jimmer.runtime.JimmerDataSourcesRecorder;
import io.quarkiverse.jimmer.runtime.QuarkusSqlClientProducer;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.CustomScopeAnnotationsBuildItem;
import io.quarkus.arc.deployment.IgnoreSplitPackageBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

@BuildSteps(onlyIf = Enabled.class)
final class JimmerProcessor {

    private static final String FEATURE = "jimmer";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerDialectsForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "org.babyfish.jimmer.sql.dialect.DefaultDialect",
                "org.babyfish.jimmer.sql.dialect.H2Dialect",
                "org.babyfish.jimmer.sql.dialect.MySql5Dialect",
                "org.babyfish.jimmer.sql.dialect.MySqlDialect",
                "org.babyfish.jimmer.sql.dialect.MySqlStyleDialect",
                "org.babyfish.jimmer.sql.dialect.OracleDialect",
                "org.babyfish.jimmer.sql.dialect.PostgresDialect",
                "org.babyfish.jimmer.sql.dialect.SQLiteDialect",
                "org.babyfish.jimmer.sql.dialect.SqlServerDialect",
                "org.babyfish.jimmer.sql.dialect.TiDBDialect")
                .constructors()
                .build());
    }

    @BuildStep(onlyIf = JavaEnabled.class)
    void indexJimmerForJava(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-core"));
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-sql"));
    }

    @BuildStep(onlyIf = KotlinEnabled.class)
    void indexJimmerForKotlin(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-core-kotlin"));
        indexDependency.produce(new IndexDependencyBuildItem("org.babyfish.jimmer", "jimmer-sql-kotlin"));
    }

    @BuildStep
    void retainJimmerExtensionPoints(BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        // These beans are discovered dynamically while constructing each SQL client.
        unremovableBeans.produce(UnremovableBeanBuildItem.beanTypes(
                org.babyfish.jimmer.sql.di.UserIdGeneratorProvider.class,
                org.babyfish.jimmer.sql.di.LogicalDeletedValueGeneratorProvider.class,
                org.babyfish.jimmer.sql.di.TransientResolverProvider.class,
                org.babyfish.jimmer.sql.di.AopProxyProvider.class,
                org.babyfish.jimmer.sql.meta.UserIdGenerator.class,
                org.babyfish.jimmer.sql.meta.LogicalDeletedValueGenerator.class,
                TransientResolver.class,
                org.babyfish.jimmer.sql.runtime.EntityManager.class,
                org.babyfish.jimmer.sql.meta.DatabaseSchemaStrategy.class,
                org.babyfish.jimmer.sql.meta.DatabaseNamingStrategy.class,
                org.babyfish.jimmer.sql.meta.MetaStringResolver.class,
                org.babyfish.jimmer.sql.dialect.Dialect.class,
                io.quarkiverse.jimmer.runtime.dialect.DialectDetector.class,
                org.babyfish.jimmer.sql.runtime.Executor.class,
                org.babyfish.jimmer.sql.runtime.SqlFormatter.class,
                org.babyfish.jimmer.sql.runtime.ConnectionManager.class,
                org.babyfish.jimmer.sql.runtime.MicroServiceExchange.class,
                org.babyfish.jimmer.sql.runtime.ScalarProvider.class,
                org.babyfish.jimmer.sql.runtime.ExceptionTranslator.class,
                org.babyfish.jimmer.sql.DraftInterceptor.class,
                org.babyfish.jimmer.sql.cache.CacheFactory.class,
                org.babyfish.jimmer.sql.cache.CacheOperator.class,
                org.babyfish.jimmer.sql.cache.CacheAbandonedCallback.class,
                org.babyfish.jimmer.sql.filter.Filter.class,
                org.babyfish.jimmer.sql.runtime.Customizer.class,
                org.babyfish.jimmer.sql.runtime.Initializer.class,
                org.babyfish.jimmer.sql.kt.filter.KFilter.class,
                org.babyfish.jimmer.sql.kt.cfg.KCustomizer.class,
                org.babyfish.jimmer.sql.kt.cfg.KInitializer.class));
        unremovableBeans.produce(new UnremovableBeanBuildItem(bean -> bean.getTypes().stream()
                .anyMatch(type -> type.kind() == Type.Kind.PARAMETERIZED_TYPE
                        && type.name().equals(DotName.createSimple(Consumer.class))
                        && type.asParameterizedType().arguments().get(0).name()
                                .equals(DotName.createSimple(JSqlClient.Builder.class)))));
    }

    @BuildStep
    IgnoreSplitPackageBuildItem splitPackages() {
        return new IgnoreSplitPackageBuildItem(List.of("org.babyfish.jimmer", "org.babyfish.jimmer.sql"));
    }

    @BuildStep
    AnnotationsTransformerBuildItem transform(CustomScopeAnnotationsBuildItem customScopes) {
        return new AnnotationsTransformerBuildItem(AnnotationTransformation.forClasses()
                .whenClass(classInfo -> classInfo.interfaceNames().contains(
                        DotName.createSimple(TransientResolver.class.getName())) && customScopes.isScopeDeclaredOn(classInfo))
                .transform(transformationContext -> {
                    transformationContext.add(AnnotationInstance.builder(Named.class)
                            .add(AnnotationValue.createStringValue("value",
                                    Introspector.decapitalize(transformationContext.declaration().asClass().simpleName())))
                            .build());
                }));
    }

    @BuildStep(onlyIf = JavaEnabled.class)
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

        additionalBeans.produce(new AdditionalBeanBuildItem(JSqlClient.class, JSqlClientImplementor.class));

        additionalBeans
                .produce(AdditionalBeanBuildItem.builder().addBeanClasses(QuarkusSqlClientProducer.class).setUnremovable()
                        .setDefaultScope(DotNames.SINGLETON).build());

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(JSqlClientImplementor.class)
                    // The ArC proxy must also implement the interface consumed by Jimmer's integration APIs.
                    .addType(JSqlClient.class)
                    .scope(ApplicationScoped.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(QuarkusSqlClientProducer.class))
                    // Defer datasource access until ArC creates the active client instance.
                    .addInjectionPoint(
                            ParameterizedType.create(InjectableInstance.class, ClassType.create(javax.sql.DataSource.class)),
                            DataSourceUtil.isDefault(dataSourceName)
                                    ? AnnotationInstance.builder(Default.class).build()
                                    : AnnotationInstance.builder(DataSource.class).add("value", dataSourceName).build())
                    .checkActive(recorder.checkActiveSupplier(dataSourceName))
                    .createWith(recorder.quarkusJSqlClientFunction(dataSourceName));

            if (DataSourceUtil.isDefault(dataSourceName)) {
                configurator.addQualifier(Default.class);
                configurator.priority(10);
            } else {
                String beanName = FEATURE + "_" + dataSourceName;
                configurator.named(beanName);
                configurator.priority(5);

                configurator.addQualifier().annotation(DataSource.class).addValue("value", dataSourceName).done();
            }

            syntheticBeanBuildItemBuildProducer.produce(configurator.done());
        }
    }

    @BuildStep(onlyIf = KotlinEnabled.class)
    @Produce(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(LoggingSetupBuildItem.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateKSqlClientBeans(JimmerDataSourcesRecorder recorder,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        additionalBeans.produce(new AdditionalBeanBuildItem(KSqlClient.class));

        additionalBeans
                .produce(AdditionalBeanBuildItem.builder().addBeanClasses(QuarkusSqlClientProducer.class).setUnremovable()
                        .setDefaultScope(DotNames.SINGLETON).build());

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(KSqlClient.class)
                    .scope(ApplicationScoped.class)
                    .setRuntimeInit()
                    .unremovable()
                    .addInjectionPoint(ClassType.create(QuarkusSqlClientProducer.class))
                    // Defer datasource access until ArC creates the active client instance.
                    .addInjectionPoint(
                            ParameterizedType.create(InjectableInstance.class, ClassType.create(javax.sql.DataSource.class)),
                            DataSourceUtil.isDefault(dataSourceName)
                                    ? AnnotationInstance.builder(Default.class).build()
                                    : AnnotationInstance.builder(DataSource.class).add("value", dataSourceName).build())
                    .checkActive(recorder.checkActiveSupplier(dataSourceName))
                    .createWith(recorder.quarkusKSqlClientFunction(dataSourceName));

            if (DataSourceUtil.isDefault(dataSourceName)) {
                configurator.addQualifier(Default.class);
                configurator.priority(10);
            } else {
                String beanName = FEATURE + "_" + dataSourceName;
                configurator.named(beanName);
                configurator.priority(5);

                configurator.addQualifier().annotation(DataSource.class).addValue("value", dataSourceName).done();
            }

            syntheticBeanBuildItemBuildProducer.produce(configurator.done());
        }
    }

    @BuildStep
    void registerNativeImageResources(BuildProducer<NativeImageResourceBuildItem> resource) {
        resource.produce(new NativeImageResourceBuildItem(
                Constant.ENTITIES_RESOURCE,
                Constant.IMMUTABLES_RESOURCE));
    }
}
