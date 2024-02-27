package io.quarkiverse.jimmer.runtime.client.openapi;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.babyfish.jimmer.client.generator.openapi.OpenApiGenerator;
import org.babyfish.jimmer.client.generator.openapi.OpenApiProperties;
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

        List<OpenApiProperties.Server> servers = null;
        if (config.client.openapi.properties.servers.isPresent()) {
            servers = new ArrayList<>(config.client.openapi.properties.servers.get().size());
            for (JimmerBuildTimeConfig.Server server : config.client.openapi.properties.servers.get()) {
                Map<String, OpenApiProperties.Variable> map = new HashMap<>();
                server.variables.forEach((k, v) -> map.put(k, new OpenApiProperties.Variable(v.enums.orElse(null),
                        v.defaultValue.orElse(null), v.description.orElse(null))));
                servers.add(new OpenApiProperties.Server(server.url.orElse(null), server.description.orElse(null), map));
            }
        }

        Map<String, OpenApiProperties.SecurityScheme> map;
        if (!config.client.openapi.properties.components.securitySchemes.isEmpty()) {
            map = new HashMap<>();
            config.client.openapi.properties.components.securitySchemes.forEach((k, v) -> map.put(k,
                    new OpenApiProperties.SecurityScheme(
                            v.type.orElse(null),
                            v.description.orElse(null),
                            v.name.orElse(null),
                            v.in,
                            v.scheme.orElse(null),
                            v.bearerFormat.orElse(null),
                            new OpenApiProperties.Flows(
                                    new OpenApiProperties.Flow(v.flows.implicit.authorizationUrl.orElse(null),
                                            v.flows.implicit.tokenUrl.orElse(null), v.flows.implicit.refreshUrl.orElse(null),
                                            v.flows.implicit.scopes),
                                    new OpenApiProperties.Flow(v.flows.password.authorizationUrl.orElse(null),
                                            v.flows.password.tokenUrl.orElse(null), v.flows.password.refreshUrl.orElse(null),
                                            v.flows.password.scopes),
                                    new OpenApiProperties.Flow(v.flows.clientCredentials.authorizationUrl.orElse(null),
                                            v.flows.clientCredentials.tokenUrl.orElse(null),
                                            v.flows.clientCredentials.refreshUrl.orElse(null),
                                            v.flows.clientCredentials.scopes),
                                    new OpenApiProperties.Flow(v.flows.authorizationCode.authorizationUrl.orElse(null),
                                            v.flows.authorizationCode.tokenUrl.orElse(null),
                                            v.flows.authorizationCode.refreshUrl.orElse(null),
                                            v.flows.authorizationCode.scopes)),
                            v.openIdConnectUrl.orElse(null))));
        } else {
            map = null;
        }

        OpenApiProperties openApiProperties = OpenApiProperties
                .newBuilder()
                .setInfo(new OpenApiProperties.Info(
                        config.client.openapi.properties.info.title.orElse(null),
                        config.client.openapi.properties.info.description.orElse(null),
                        config.client.openapi.properties.info.termsOfService.orElse(null),
                        new OpenApiProperties.Contact(
                                config.client.openapi.properties.info.contact.name.orElse(null),
                                config.client.openapi.properties.info.contact.url.orElse(null),
                                config.client.openapi.properties.info.contact.email.orElse(null)),
                        new OpenApiProperties.License(
                                config.client.openapi.properties.info.license.name.orElse(null),
                                config.client.openapi.properties.info.license.identifier.orElse(null)),
                        config.client.openapi.properties.info.version.orElse(null)))
                .setServers(servers)
                .setComponents(new OpenApiProperties.Components(map))
                .build();

        OpenApiGenerator generator = new OpenApiGenerator(metadata, openApiProperties);

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
