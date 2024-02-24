package io.quarkiverse.jimmer.runtime.client.openapi;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.babyfish.jimmer.client.generator.openapi.OpenApiGenerator;
import org.babyfish.jimmer.client.runtime.Metadata;
import org.jboss.logging.Logger;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.Metadatas;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptHandler;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class OpenApiHandler implements Handler<RoutingContext> {

    private static final Logger log = Logger.getLogger(TypeScriptHandler.class);

    private JimmerBuildTimeConfig config;

    private boolean setup = false;

    @Override
    public void handle(RoutingContext routingContext) {
        if (!setup) {
            setup();
        }

        Metadata metadata = Metadatas.create(false, routingContext.request().getParam("groups"),
                config.client.uriPrefix.orElse(null),
                config.client.controllerNullityChecked);
        OpenApiGenerator generator = new OpenApiGenerator(metadata, null);
        HttpServerResponse response = routingContext.response();
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            doHandle(response, generator);
        } else {
            requestContext.activate();
            try {
                doHandle(response, generator);
            } finally {
                requestContext.terminate();
            }
        }

    }

    private void doHandle(HttpServerResponse response, OpenApiGenerator generator) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8);
        generator.generate(writer);

        response.putHeader(HttpHeaders.CONTENT_TYPE, Constant.APPLICATION_YML)
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
