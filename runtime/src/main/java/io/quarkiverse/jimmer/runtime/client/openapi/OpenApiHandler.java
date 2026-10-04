package io.quarkiverse.jimmer.runtime.client.openapi;

import org.babyfish.jimmer.client.runtime.Metadata;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.Metadatas;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

public class OpenApiHandler implements Handler<RoutingContext> {

    private final JimmerBuildTimeConfig buildTimeConfig;

    public OpenApiHandler(JimmerBuildTimeConfig buildTimeConfig) {
        this.buildTimeConfig = buildTimeConfig;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Metadata metadata = Metadatas.create(false, routingContext.request().getParam("groups"),
                buildTimeConfig.client().uriPrefix().orElse(null));
        byte[] document = OpenApiDocumentGenerator.generate(metadata, buildTimeConfig);
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, Constant.APPLICATION_YML)
                .end(Buffer.buffer(document));
    }
}
