package io.quarkiverse.jimmer.deployment;

import java.util.Set;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterAssociatedIdsRecorder;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsRecorder;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;

final class JimmerMicroserviceProcessor {

    private static final String EXCHANGE = "io.quarkiverse.jimmer.runtime.cloud.QuarkusExchange";
    private static final String IDS = "io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsHandler";
    private static final String ASSOCIATED_IDS = "io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterAssociatedIdsHandler";

    private static boolean enabled(JimmerBuildTimeConfig config) {
        return config.enable() && config.microServiceName().filter(name -> !name.isEmpty()).isPresent();
    }

    @BuildStep
    void registerBeans(JimmerBuildTimeConfig config, Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> beans, BuildProducer<AdditionalIndexedClassesBuildItem> indexed,
            BuildProducer<ExcludedTypeBuildItem> excluded) {
        if (!enabled(config)) {
            excluded.produce(new ExcludedTypeBuildItem(EXCHANGE));
            excluded.produce(new ExcludedTypeBuildItem(IDS));
            excluded.produce(new ExcludedTypeBuildItem(ASSOCIATED_IDS));
            return;
        }
        requireCapabilities(capabilities);
        beans.produce(AdditionalBeanBuildItem.builder().addBeanClasses(EXCHANGE, IDS, ASSOCIATED_IDS).setUnremovable().build());
        indexed.produce(new AdditionalIndexedClassesBuildItem("io.quarkiverse.jimmer.runtime.cloud.ExchangeRestClient"));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerRoutes(JimmerBuildTimeConfig config, Capabilities capabilities, BeanContainerBuildItem container,
            MicroServiceExporterIdsRecorder ids, MicroServiceExporterAssociatedIdsRecorder associatedIds,
            BuildProducer<RouteBuildItem> routes) {
        if (!enabled(config)) {
            return;
        }
        requireCapabilities(capabilities);
        routes.produce(RouteBuildItem.newManagementRoute(Constant.BY_IDS).withRouteCustomizer(ids.route())
                .withRequestHandler(ids.getHandler(container.getValue())).asBlockingRoute().build());
        routes.produce(RouteBuildItem.newManagementRoute(Constant.BY_ASSOCIATED_IDS).withRouteCustomizer(associatedIds.route())
                .withRequestHandler(associatedIds.getHandler(container.getValue())).asBlockingRoute().build());
    }

    private static void requireCapabilities(Capabilities capabilities) {
        if (capabilities.isMissing(Capability.VERTX_HTTP) || capabilities.isMissing(Capability.REST_CLIENT_REACTIVE)) {
            throw new ConfigurationException("quarkus.jimmer.micro-service-name enables the HTTP microservice bridge "
                    + "and requires quarkus-vertx-http and quarkus-rest-client",
                    Set.of("quarkus.jimmer.micro-service-name"));
        }
    }
}
