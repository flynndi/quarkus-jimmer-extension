package io.quarkiverse.jimmer.runtime.client.ts;

import java.io.ByteArrayOutputStream;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.babyfish.jimmer.client.generator.ts.TypeScriptContext;
import org.babyfish.jimmer.client.runtime.Metadata;
import org.jboss.logging.Logger;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.Metadatas;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class TypeScriptHandler implements Handler<RoutingContext> {

    private static final Logger log = Logger.getLogger(TypeScriptHandler.class);

    private JimmerBuildTimeConfig buildTimeConfig;

    private boolean setup = false;

    @Override
    public void handle(RoutingContext routingContext) {
        if (!setup) {
            setup();
        }

        JimmerBuildTimeConfig.TypeScript ts = buildTimeConfig.client().ts();
        Metadata metadata = Metadatas.create(true, routingContext.request().getParam("groups"),
                buildTimeConfig.client().uriPrefix().orElse(null),
                buildTimeConfig.client().controllerNullityChecked());
        TypeScriptContext ctx = new TypeScriptContext(metadata, ts.indent(), ts.mutable(), ts.apiName(),
                ts.nullRenderMode(), ts.isEnumTsStyle());
        HttpServerResponse response = routingContext.response();
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            doHandle(response, ctx);
        } else {
            requestContext.activate();
            try {
                doHandle(response, ctx);
            } finally {
                requestContext.terminate();
            }
        }
    }

    private void doHandle(HttpServerResponse response, TypeScriptContext context) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        context.renderAll(byteArrayOutputStream);

        response.putHeader(HttpHeaders.CONTENT_TYPE, Constant.APPLICATION_ZIP)
                .end(Buffer.buffer(byteArrayOutputStream.toByteArray()));
    }

    private void setup() {
        Instance<JimmerBuildTimeConfig> buildTimeConfigs = CDI.current().select(JimmerBuildTimeConfig.class,
                Default.Literal.INSTANCE);

        if (buildTimeConfigs.isUnsatisfied()) {
            buildTimeConfig = null;
        } else if (buildTimeConfigs.isAmbiguous()) {
            buildTimeConfig = buildTimeConfigs.iterator().next();
            log.warnf("Multiple JimmerBuildTimeConfig registries present. Using %s with the built in scrape endpoint",
                    buildTimeConfigs);
        } else {
            buildTimeConfig = buildTimeConfigs.get();
        }

        setup = true;
    }
}
