package io.quarkiverse.jimmer.runtime.client.openapi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class CssHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            try {
                doHandle(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            requestContext.activate();
            try {
                doHandle(response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                requestContext.terminate();
            }
        }
    }

    private void doHandle(HttpServerResponse response) throws IOException {
        byte[] buf = new byte[4 * 1024];
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(Constant.CSS_RESOURCE);
        if (in == null) {
            throw new IllegalStateException("The resource \"" + Constant.CSS_RESOURCE + "\" does not exist");
        }
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            int len;
            while ((len = in.read(buf)) != -1) {
                byteArrayOutputStream.write(buf, 0, len);
            }
            byteArrayOutputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            in.close();
        }
        response.putHeader(HttpHeaders.CONTENT_TYPE, Constant.TEXT_CSS)
                .end(Buffer.buffer(byteArrayOutputStream.toByteArray()));
    }
}
