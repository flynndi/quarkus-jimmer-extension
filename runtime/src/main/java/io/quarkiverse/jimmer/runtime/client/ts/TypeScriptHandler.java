package io.quarkiverse.jimmer.runtime.client.ts;

import java.io.ByteArrayOutputStream;

import org.babyfish.jimmer.client.generator.ts.TypeScriptContext;
import org.babyfish.jimmer.client.runtime.Metadata;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.Metadatas;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class TypeScriptHandler implements Handler<RoutingContext> {

    private final JimmerBuildTimeConfig buildTimeConfig;

    public TypeScriptHandler(JimmerBuildTimeConfig buildTimeConfig) {
        this.buildTimeConfig = buildTimeConfig;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        JimmerBuildTimeConfig.TypeScript ts = buildTimeConfig.client().ts();
        Metadata metadata = Metadatas.create(true, routingContext.request().getParam("groups"),
                buildTimeConfig.client().uriPrefix().orElse(null));
        TypeScriptContext ctx = new TypeScriptContext(metadata, ts.indent(), ts.mutable(), ts.apiName(),
                ts.nullRenderMode(), ts.isEnumTsStyle());
        HttpServerResponse response = routingContext.response();
        doHandle(response, ctx);

    }

    private void doHandle(HttpServerResponse response, TypeScriptContext context) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        context.renderAll(byteArrayOutputStream);

        response.putHeader(HttpHeaders.CONTENT_TYPE, Constant.APPLICATION_ZIP)
                .end(Buffer.buffer(byteArrayOutputStream.toByteArray()));
    }

}
