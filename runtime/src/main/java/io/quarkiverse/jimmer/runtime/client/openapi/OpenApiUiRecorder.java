package io.quarkiverse.jimmer.runtime.client.openapi;

import java.util.function.Consumer;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class OpenApiUiRecorder {

    public Handler<RoutingContext> getHandler(JimmerBuildTimeConfig config) {
        return getHandler(config, config.client().openapi().refPath().or(() -> config.client().openapi().path()).orElse(null));
    }

    public Handler<RoutingContext> getHandler(JimmerBuildTimeConfig config, String resolvedRefPath) {
        return new OpenApiUiHandler(config, resolvedRefPath);
    }

    public Consumer<Route> route() {
        return new Consumer<Route>() {
            @Override
            public void accept(Route route) {
                route.order(1).method(HttpMethod.GET);
            }
        };
    }
}
