package io.quarkiverse.jimmer.deployment;

import java.util.List;
import java.util.Set;

import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Default;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.babyfish.jimmer.sql.runtime.MicroServiceExchange;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterAssociatedIdsRecorder;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsRecorder;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BeanResolver;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;

final class JimmerMicroserviceProcessor {

    private static final String EXCHANGE = "io.quarkiverse.jimmer.runtime.cloud.QuarkusExchange";
    private static final String IDS = "io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsHandler";
    private static final String ASSOCIATED_IDS = "io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterAssociatedIdsHandler";

    @BuildStep
    void registerBeans(JimmerBuildTimeConfig config, Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> beans, BuildProducer<AdditionalIndexedClassesBuildItem> indexed,
            BuildProducer<ExcludedTypeBuildItem> excluded, BuildProducer<ReflectiveClassBuildItem> reflection) {
        boolean exchangeEnabled = JimmerMicroserviceSupport.httpExchangeEnabled(config, capabilities);
        boolean exporterEnabled = JimmerMicroserviceSupport.exporterEnabled(config, capabilities);
        if (exchangeEnabled) {
            beans.produce(AdditionalBeanBuildItem.builder().addBeanClasses(EXCHANGE).setUnremovable().build());
            indexed.produce(new AdditionalIndexedClassesBuildItem("io.quarkiverse.jimmer.runtime.cloud.ExchangeRestClient"));
        } else {
            excluded.produce(new ExcludedTypeBuildItem(EXCHANGE));
        }
        if (exporterEnabled) {
            beans.produce(AdditionalBeanBuildItem.builder().addBeanClasses(IDS, ASSOCIATED_IDS).setUnremovable().build());
        } else {
            excluded.produce(new ExcludedTypeBuildItem(IDS));
            excluded.produce(new ExcludedTypeBuildItem(ASSOCIATED_IDS));
        }
        if (exchangeEnabled || exporterEnabled) {
            // Both HTTP directions use Tuple2 JSON without a typed REST response signature.
            reflection.produce(ReflectiveClassBuildItem.builder("org.babyfish.jimmer.sql.ast.tuple.Tuple2")
                    .constructors().methods().build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerRoutes(JimmerBuildTimeConfig config, Capabilities capabilities, BeanContainerBuildItem container,
            MicroServiceExporterIdsRecorder ids, MicroServiceExporterAssociatedIdsRecorder associatedIds,
            BuildProducer<RouteBuildItem> routes) {
        if (!JimmerMicroserviceSupport.exporterEnabled(config, capabilities)) {
            return;
        }
        routes.produce(RouteBuildItem.newManagementRoute(Constant.BY_IDS).withRouteCustomizer(ids.route())
                .withRequestHandler(ids.getHandler(container.getValue())).asBlockingRoute().build());
        routes.produce(RouteBuildItem.newManagementRoute(Constant.BY_ASSOCIATED_IDS).withRouteCustomizer(associatedIds.route())
                .withRequestHandler(associatedIds.getHandler(container.getValue())).asBlockingRoute().build());
    }

    @BuildStep
    void validateExchanges(JimmerBuildTimeConfig config,
            List<JdbcDataSourceBuildItem> dataSources, ValidationPhaseBuildItem validation,
            BuildProducer<ValidationErrorBuildItem> errors) {
        if (!JimmerMicroserviceSupport.enabled(config) || dataSources.isEmpty()) {
            return;
        }
        // Only registered managed clients need an exchange. Inspect CDI metadata without constructing any beans.
        BeanResolver resolver = validation.getBeanResolver();
        for (JdbcDataSourceBuildItem dataSource : dataSources) {
            if (!usesManagedClient(config, resolver, dataSource.getName())) {
                continue;
            }
            Set<BeanInfo> candidates = resolver.resolveBeans(ClassType.create(MicroServiceExchange.class),
                    AnnotationInstance.builder(DataSource.class).add("value", dataSource.getName()).build());
            if (candidates.isEmpty()) {
                candidates = resolver.resolveBeans(ClassType.create(MicroServiceExchange.class),
                        AnnotationInstance.builder(Default.class).build());
            }
            try {
                if (resolver.resolveAmbiguity(candidates) == null) {
                    errors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                            "quarkus.jimmer.micro-service-name requires a MicroServiceExchange for datasource '"
                                    + dataSource.getName()
                                    + "'. Provide a matching CDI bean or add quarkus-rest-client and quarkus-jackson "
                                    + "for the default HTTP exchange.",
                            Set.of("quarkus.jimmer.micro-service-name"))));
                }
            } catch (AmbiguousResolutionException ex) {
                errors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                        "Ambiguous MicroServiceExchange beans for datasource '" + dataSource.getName()
                                + "' configured with quarkus.jimmer.micro-service-name: " + ex.getMessage(),
                        Set.of("quarkus.jimmer.micro-service-name"))));
            }
        }
    }

    /**
     * Limits exchange validation to clients created by this extension. Application-provided clients may
     * configure their exchange directly instead of exposing it as a CDI bean. Resolve metadata only so
     * this build-time check never initializes a client.
     */
    private static boolean usesManagedClient(JimmerBuildTimeConfig config, BeanResolver resolver, String dataSourceName) {
        boolean kotlin = "kotlin".equalsIgnoreCase(config.language());
        AnnotationInstance qualifier = DataSourceUtil.isDefault(dataSourceName)
                ? AnnotationInstance.builder(Default.class).build()
                : AnnotationInstance.builder(DataSource.class).add("value", dataSourceName).build();
        BeanInfo client;
        try {
            client = resolver.resolveAmbiguity(resolver.resolveBeans(
                    ClassType.create(kotlin ? KSqlClient.class : JSqlClient.class), qualifier));
        } catch (AmbiguousResolutionException ex) {
            // SQL client selection is separate; do not impose our factory's requirements on custom clients.
            return false;
        }
        return client != null && client.isSynthetic()
                && client.getBeanClass().toString()
                        .equals(kotlin ? KSqlClient.class.getName() : JSqlClientImplementor.class.getName());
    }
}
