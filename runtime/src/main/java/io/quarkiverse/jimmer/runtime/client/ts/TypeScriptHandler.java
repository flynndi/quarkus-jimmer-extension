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

    private JimmerBuildTimeConfig config;

    private boolean setup = false;

    @Override
    public void handle(RoutingContext routingContext) {
        if (!setup) {
            setup();
        }

        JimmerBuildTimeConfig.TypeScript ts = config.client().get().ts();
        Metadata metadata = Metadatas.create(true, routingContext.request().getParam("groups"),
                config.client().get().uriPrefix.orElse(null),
                config.client().get().controllerNullityChecked());
        TypeScriptContext ctx = new TypeScriptContext(metadata, ts.indent(), ts.mutable(), ts.apiName().get(),
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
        Instance<JimmerBuildTimeConfig> configs = CDI.current().select(JimmerBuildTimeConfig.class,
                Default.Literal.INSTANCE);

        if (configs.isUnsatisfied()) {
            config = null;
        } else if (configs.isAmbiguous()) {
            config = configs.iterator().next();
            log.warnf("Multiple JimmerBuildTimeConfig registries present. Using %s with the built in scrape endpoint", configs);
        } else {
            config = configs.get();
        }

        setup = true;
    }
}
