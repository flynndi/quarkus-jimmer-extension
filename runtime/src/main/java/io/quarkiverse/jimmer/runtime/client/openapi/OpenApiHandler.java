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

    private JimmerBuildTimeConfig buildTimeConfig;

    private boolean setup = false;

    @Override
    public void handle(RoutingContext routingContext) {
        if (!setup) {
            setup();
        }

        Metadata metadata = Metadatas.create(false, routingContext.request().getParam("groups"),
                buildTimeConfig.client().uriPrefix().orElse(null));

        List<OpenApiProperties.Server> servers = null;
        if (buildTimeConfig.client().openapi().properties().servers().isPresent()) {
            servers = new ArrayList<>(buildTimeConfig.client().openapi().properties().servers().get().size());
            for (JimmerBuildTimeConfig.Server server : buildTimeConfig.client().openapi().properties().servers().get()) {
                Map<String, OpenApiProperties.Variable> map = new HashMap<>();
                server.variables().forEach((k, v) -> map.put(k, new OpenApiProperties.Variable(v.enums().orElse(null),
                        v.defaultValue().orElse(null), v.description().orElse(null))));
                servers.add(new OpenApiProperties.Server(server.url().orElse(null), server.description().orElse(null), map));
            }
        }

        Map<String, OpenApiProperties.SecurityScheme> map;
        if (!buildTimeConfig.client().openapi().properties().components().securitySchemes().isEmpty()) {
            map = new HashMap<>();
            buildTimeConfig.client().openapi().properties().components().securitySchemes().forEach((k, v) -> map.put(k,
                    new OpenApiProperties.SecurityScheme(
                            v.type().orElse(null),
                            v.description().orElse(null),
                            v.name().orElse(null),
                            v.in(),
                            v.scheme().orElse(null),
                            v.bearerFormat().orElse(null),
                            new OpenApiProperties.Flows(
                                    v.flows().implicit().isEmpty() ? null
                                            : new OpenApiProperties.Flow(
                                                    v.flows().implicit().get().authorizationUrl().orElse(null),
                                                    v.flows().implicit().get().tokenUrl().orElse(null),
                                                    v.flows().implicit().get().refreshUrl().orElse(null),
                                                    v.flows().implicit().get().scopes()),
                                    v.flows().password().isEmpty() ? null
                                            : new OpenApiProperties.Flow(
                                                    v.flows().password().get().authorizationUrl().orElse(null),
                                                    v.flows().password().get().tokenUrl().orElse(null),
                                                    v.flows().password().get().refreshUrl().orElse(null),
                                                    v.flows().password().get().scopes()),
                                    v.flows().clientCredentials().isEmpty() ? null
                                            : new OpenApiProperties.Flow(
                                                    v.flows().clientCredentials().get().authorizationUrl().orElse(null),
                                                    v.flows().clientCredentials().get().tokenUrl().orElse(null),
                                                    v.flows().clientCredentials().get().refreshUrl().orElse(null),
                                                    v.flows().clientCredentials().get().scopes()),
                                    v.flows().authorizationCode().isEmpty() ? null
                                            : new OpenApiProperties.Flow(
                                                    v.flows().authorizationCode().get().authorizationUrl().orElse(null),
                                                    v.flows().authorizationCode().get().tokenUrl().orElse(null),
                                                    v.flows().authorizationCode().get().refreshUrl().orElse(null),
                                                    v.flows().authorizationCode().get().scopes())),
                            v.openIdConnectUrl().orElse(null))));
        } else {
            map = null;
        }

        OpenApiProperties openApiProperties = OpenApiProperties
                .newBuilder()
                .setInfo(new OpenApiProperties.Info(
                        buildTimeConfig.client().openapi().properties().info().title().orElse(null),
                        buildTimeConfig.client().openapi().properties().info().description().orElse(null),
                        buildTimeConfig.client().openapi().properties().info().termsOfService().orElse(null),
                        new OpenApiProperties.Contact(
                                buildTimeConfig.client().openapi().properties().info().contact().name().orElse(null),
                                buildTimeConfig.client().openapi().properties().info().contact().url().orElse(null),
                                buildTimeConfig.client().openapi().properties().info().contact().email().orElse(null)),
                        new OpenApiProperties.License(
                                buildTimeConfig.client().openapi().properties().info().license().name().orElse(null),
                                buildTimeConfig.client().openapi().properties().info().license().identifier().orElse(null)),
                        buildTimeConfig.client().openapi().properties().info().version().orElse(null)))
                .setServers(servers)
                .setComponents(new OpenApiProperties.Components(map))
                .setSecurities(buildTimeConfig.client().openapi().properties().securities().orElse(null))
                .build();

        OpenApiGenerator generator = new OpenApiGenerator(metadata, openApiProperties) {
            @Override
            protected int errorHttpStatus() {
                return buildTimeConfig.errorTranslator().isEmpty() ? 500 : buildTimeConfig.errorTranslator().get().httpStatus();
            }
        };

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
