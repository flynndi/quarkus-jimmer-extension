package io.quarkiverse.jimmer.runtime.client.ts;

import java.util.function.Consumer;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.ext.web.Route;

@Recorder
public class TypeScriptRecorder {

    TypeScriptHandler handler;

    public TypeScriptHandler getHandler() {
        if (handler == null) {
            handler = new TypeScriptHandler();
        }

        return handler;
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
