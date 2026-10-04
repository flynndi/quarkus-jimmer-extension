package io.quarkiverse.jimmer.runtime.client.ts;

import org.babyfish.jimmer.client.runtime.Metadata;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.Metadatas;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

public class TypeScriptHandler implements Handler<RoutingContext> {

    private final JimmerBuildTimeConfig buildTimeConfig;

    public TypeScriptHandler(JimmerBuildTimeConfig buildTimeConfig) {
        this.buildTimeConfig = buildTimeConfig;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Metadata metadata = Metadatas.create(true, routingContext.request().getParam("groups"),
                buildTimeConfig.client().uriPrefix().orElse(null));
        byte[] archive = TypeScriptGenerator.generate(metadata, buildTimeConfig.client().ts());
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, Constant.APPLICATION_ZIP)
                .end(Buffer.buffer(archive));
    }
}
