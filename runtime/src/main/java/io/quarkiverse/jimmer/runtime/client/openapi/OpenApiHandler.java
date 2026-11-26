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

    private final OpenApiDocumentGenerator generator;

    private final String uriPrefix;

    public OpenApiHandler(JimmerBuildTimeConfig buildTimeConfig) {
        this.generator = OpenApiDocumentGenerator.from(buildTimeConfig);
        this.uriPrefix = buildTimeConfig.client().uriPrefix().orElse(null);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Metadata metadata = Metadatas.create(false, routingContext.request().getParam("groups"),
                uriPrefix);
        byte[] document = generator.generate(metadata);
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, Constant.APPLICATION_YML)
                .end(Buffer.buffer(document));
    }
}
