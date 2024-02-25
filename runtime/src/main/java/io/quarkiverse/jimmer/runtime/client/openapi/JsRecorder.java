package io.quarkiverse.jimmer.runtime.client.openapi;

import java.util.function.Consumer;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;

@Recorder
public class JsRecorder {

    JsHandler handler;

    public JsHandler getHandler() {
        if (handler == null) {
            handler = new JsHandler();
        }

        return handler;
    }

    public Consumer<Route> route() {
        return new Consumer<Route>() {
            @Override
            public void accept(Route route) {
                route.order(1).produces(HttpMethod.GET.name());
            }
        };
    }
}
