package io.quarkiverse.jimmer.deployment;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkiverse.jimmer.runtime.client.openapi.CssRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.JsRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiUiRecorder;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptRecorder;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;

final class JimmerWebProcessor {

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerRoutes(JimmerBuildTimeConfig config, Capabilities capabilities, LaunchModeBuildItem launchMode,
            TypeScriptRecorder ts, OpenApiRecorder openapi, OpenApiUiRecorder ui, CssRecorder css, JsRecorder js,
            BuildProducer<RouteBuildItem> routes, BuildProducer<RegistryBuildItem> registries) {
        if (!config.enable()) {
            return;
        }
        // Arc's validation step may run later than route construction; reject invalid paths before using them here.
        JimmerConfigValidator.validateBuildTime(config);
        var client = config.client();
        if (client.ts().path().isEmpty() && client.openapi().path().isEmpty() && client.openapi().uiPath().isEmpty()) {
            return;
        }
        if (capabilities.isMissing(Capability.VERTX_HTTP)) {
            Set<String> keys = new LinkedHashSet<>();
            client.ts().path().ifPresent(path -> keys.add("quarkus.jimmer.client.ts.path"));
            client.openapi().path().ifPresent(path -> keys.add("quarkus.jimmer.client.openapi.path"));
            client.openapi().uiPath().ifPresent(path -> keys.add("quarkus.jimmer.client.openapi.ui-path"));
            throw new ConfigurationException("Configured Jimmer client endpoints " + keys + " require quarkus-vertx-http "
                    + "(also supplied by quarkus-rest); add an HTTP extension or remove these paths", keys);
        }
        client.ts().path().ifPresent(path -> {
            routes.produce(RouteBuildItem.newManagementRoute(path).withRouteCustomizer(ts.route())
                    .withRoutePathConfigKey("quarkus.jimmer.client.ts.path").withRequestHandler(ts.getHandler(config))
                    .asBlockingRoute().build());
            registries.produce(new RegistryBuildItem("TypeScriptResource", JimmerHttpPaths.managementUrl(path, launchMode)));
        });
        client.openapi().path().ifPresent(path -> {
            routes.produce(RouteBuildItem.newManagementRoute(path).withRouteCustomizer(openapi.route())
                    .withRoutePathConfigKey("quarkus.jimmer.client.openapi.path")
                    .withRequestHandler(openapi.getHandler(config)).asBlockingRoute().build());
            registries.produce(new RegistryBuildItem("OpenApiResource", JimmerHttpPaths.managementUrl(path, launchMode)));
        });
        client.openapi().uiPath().ifPresent(path -> {
            // A local specification is on the same server as the UI, including a separate management server.
            // Preserve an explicitly configured reference (which may point at an external server) verbatim.
            String ref = client.openapi().refPath().orElseGet(() -> URI.create(JimmerHttpPaths.managementUrl(
                    client.openapi().path().orElseThrow(), launchMode)).getRawPath());
            routes.produce(RouteBuildItem.newManagementRoute(path).withRouteCustomizer(ui.route())
                    .withRoutePathConfigKey("quarkus.jimmer.client.openapi.ui-path")
                    .withRequestHandler(ui.getHandler(config, ref)).asBlockingRoute().build());
            routes.produce(RouteBuildItem.newManagementRoute(Constant.CSS_URL).withRouteCustomizer(css.route())
                    .withRequestHandler(css.getHandler()).asBlockingRoute().build());
            routes.produce(RouteBuildItem.newManagementRoute(Constant.JS_URL).withRouteCustomizer(js.route())
                    .withRequestHandler(js.getHandler()).asBlockingRoute().build());
            registries.produce(new RegistryBuildItem("OpenApiUiResource", JimmerHttpPaths.managementUrl(path, launchMode)));
        });
    }
}
