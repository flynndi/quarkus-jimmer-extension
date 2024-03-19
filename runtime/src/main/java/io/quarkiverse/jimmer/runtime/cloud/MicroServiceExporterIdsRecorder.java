package io.quarkiverse.jimmer.runtime.cloud;

import java.util.function.Consumer;

import jakarta.ws.rs.core.MediaType;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.ext.web.Route;

@Recorder
public class MicroServiceExporterIdsRecorder {

    MicroServiceExporterIdsHandler handler;

    public AbstractMicroServiceExporterHandler getHandler() {
        if (handler == null) {
            return new MicroServiceExporterIdsHandler();
        }

        return handler;
    }

    public Consumer<Route> route() {
        return new Consumer<Route>() {
            @Override
            public void accept(Route route) {
                route.order(1).produces(MediaType.APPLICATION_JSON);
            }
        };
    }
}
