package io.quarkiverse.jimmer.runtime.client.ts;

import java.util.function.Consumer;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class TypeScriptRecorder {

    public Handler<RoutingContext> getHandler(JimmerBuildTimeConfig config) {
        return new TypeScriptHandler(config);
    }

    public Consumer<Route> route() {
        return new Consumer<Route>() {
            @Override
            public void accept(Route route) {
                route.order(1).produces(Constant.APPLICATION_ZIP);
            }
        };
    }
}
