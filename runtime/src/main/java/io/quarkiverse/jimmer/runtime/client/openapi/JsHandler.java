package io.quarkiverse.jimmer.runtime.client.openapi;

import java.io.IOException;
import java.io.InputStream;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

public class JsHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(Constant.JS_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("The resource \"" + Constant.JS_RESOURCE + "\" does not exist");
            }
            routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, Constant.TEXT_JAVASCRIPT)
                    .end(Buffer.buffer(stream.readAllBytes()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read resource \"" + Constant.JS_RESOURCE + "\"", e);
        }
    }
}
