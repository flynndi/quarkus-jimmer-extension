package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.client.openapi.CssRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.JsRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiUiRecorder;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;

class OpenApiRoutesTest {

    @Test
    void restrictsRoutesToGetWithoutAdvertisingAnHttpVerbAsAMediaType() {
        List<Consumer<Route>> customizers = List.of(new OpenApiRecorder().route(), new OpenApiUiRecorder().route(),
                new JsRecorder().route(), new CssRecorder().route());
        for (Consumer<Route> customizer : customizers) {
            List<HttpMethod> methods = new ArrayList<>();
            List<String> mediaTypes = new ArrayList<>();
            Route route = (Route) Proxy.newProxyInstance(Route.class.getClassLoader(), new Class<?>[] { Route.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("method")) {
                            methods.add((HttpMethod) args[0]);
                        } else if (method.getName().equals("produces")) {
                            mediaTypes.add((String) args[0]);
                        }
                        return proxy;
                    });
            customizer.accept(route);
            assertEquals(List.of(HttpMethod.GET), methods);
            assertTrue(mediaTypes.isEmpty());
        }
    }
}
