package io.quarkiverse.jimmer.test.client;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

final class HttpTestResponse {
    final Map<String, String> headers = new HashMap<>();
    Buffer body;

    RoutingContext context(Map<String, String> parameters) {
        HttpServerRequest request = (HttpServerRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServerRequest.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getParam")) {
                        return parameters.get(args[0]);
                    }
                    throw new AssertionError("Unexpected request operation: " + method);
                });
        HttpServerResponse response = (HttpServerResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { HttpServerResponse.class }, (proxy, method, args) -> {
                    if (method.getName().equals("putHeader")) {
                        headers.put(args[0].toString(), args[1].toString());
                        return proxy;
                    }
                    if (method.getName().equals("end")) {
                        body = (Buffer) args[0];
                        return Future.succeededFuture();
                    }
                    throw new AssertionError("Unexpected response operation: " + method);
                });
        return (RoutingContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { RoutingContext.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "request" -> request;
                    case "response" -> response;
                    default -> throw new AssertionError("Unexpected routing operation: " + method);
                });
    }
}
