package io.quarkiverse.jimmer.runtime.client.openapi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.client.generator.openapi.OpenApiGenerator;
import org.babyfish.jimmer.client.generator.openapi.OpenApiProperties;
import org.babyfish.jimmer.client.runtime.Metadata;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerOpenApiConfig;

/** Generates Jimmer OpenAPI documents using a snapshot of the configured document properties. */
public final class OpenApiDocumentGenerator {

    private final OpenApiProperties properties;

    private final int errorHttpStatus;

    public OpenApiDocumentGenerator(JimmerOpenApiConfig.Properties properties, int errorHttpStatus) {
        this.properties = convert(properties);
        this.errorHttpStatus = errorHttpStatus;
    }

    /** Generates one document without requiring an HTTP handler or a reusable generator instance. */
    public static byte[] generate(Metadata metadata, JimmerBuildTimeConfig buildTimeConfig) {
        return new OpenApiDocumentGenerator(buildTimeConfig.client().openapi().properties(),
                buildTimeConfig.errorTranslator().map(JimmerBuildTimeConfig.ErrorTranslator::httpStatus).orElse(500))
                .generate(metadata);
    }

    public byte[] generate(Metadata metadata) {
        // Jimmer's generator tracks rendered schemas and allocated names, so each document needs its own instance.
        OpenApiGenerator generator = new OpenApiGenerator(metadata, properties) {
            @Override
            protected int errorHttpStatus() {
                return errorHttpStatus;
            }
        };

        StringBuilder output = new StringBuilder();
        generator.generate(output);
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static OpenApiProperties convert(JimmerOpenApiConfig.Properties properties) {
        var info = properties.info();
        var contact = info.contact();
        var license = info.license();
        OpenApiProperties.Contact mappedContact = contact.name().isEmpty() && contact.url().isEmpty()
                && contact.email().isEmpty()
                        ? null
                        : new OpenApiProperties.Contact(contact.name().orElse(null), contact.url().orElse(null),
                                contact.email().orElse(null));
        OpenApiProperties.License mappedLicense = license.name().isEmpty() && license.identifier().isEmpty() ? null
                : new OpenApiProperties.License(license.name().orElse(null), license.identifier().orElse(null));

        // An Info object bypasses Jimmer's defaults, including when only one info property was configured.
        OpenApiProperties.Info mappedInfo = new OpenApiProperties.Info(
                info.title().orElse("<No title>"),
                info.description().orElse("<No Description>"),
                info.termsOfService().orElse(null),
                mappedContact,
                mappedLicense,
                info.version().orElse("1.0.0"));

        List<OpenApiProperties.Server> servers = new ArrayList<>();
        for (var server : properties.servers().orElse(List.of())) {
            Map<String, OpenApiProperties.Variable> variables = new LinkedHashMap<>();
            server.variables().forEach((name, variable) -> variables.put(name, new OpenApiProperties.Variable(
                    variable.enums().map(List::copyOf).orElse(null),
                    variable.defaultValue().orElse(null), variable.description().orElse(null))));
            servers.add(new OpenApiProperties.Server(server.url().orElse(null), server.description().orElse(null), variables));
        }

        Map<String, OpenApiProperties.SecurityScheme> securitySchemes = new LinkedHashMap<>();
        properties.components().securitySchemes().forEach((name, scheme) -> {
            var flows = scheme.flows();
            OpenApiProperties.Flow implicit = flows.implicit().map(OpenApiDocumentGenerator::convertFlow).orElse(null);
            OpenApiProperties.Flow password = flows.password().map(OpenApiDocumentGenerator::convertFlow).orElse(null);
            OpenApiProperties.Flow clientCredentials = flows.clientCredentials().map(OpenApiDocumentGenerator::convertFlow)
                    .orElse(null);
            OpenApiProperties.Flow authorizationCode = flows.authorizationCode().map(OpenApiDocumentGenerator::convertFlow)
                    .orElse(null);
            OpenApiProperties.Flows mappedFlows = implicit == null && password == null && clientCredentials == null
                    && authorizationCode == null ? null
                            : new OpenApiProperties.Flows(implicit, password, clientCredentials, authorizationCode);
            securitySchemes.put(name, new OpenApiProperties.SecurityScheme(
                    scheme.type().orElse(null),
                    scheme.description().orElse(null),
                    scheme.name().orElse(null),
                    switch (scheme.in()) {
                        case QUERY -> OpenApiProperties.In.QUERY;
                        case HEADER -> OpenApiProperties.In.HEADER;
                        case COOKIE -> OpenApiProperties.In.COOKIE;
                    },
                    scheme.scheme().orElse(null),
                    scheme.bearerFormat().orElse(null),
                    mappedFlows,
                    scheme.openIdConnectUrl().orElse(null)));
        });

        List<Map<String, List<String>>> securities = new ArrayList<>();
        for (var security : properties.securities().orElse(List.of())) {
            Map<String, List<String>> scopes = new LinkedHashMap<>();
            security.forEach((name, values) -> scopes.put(name, List.copyOf(values)));
            securities.add(scopes);
        }
        return new OpenApiProperties(mappedInfo, servers, securities,
                securitySchemes.isEmpty() ? null : new OpenApiProperties.Components(securitySchemes));
    }

    private static OpenApiProperties.Flow convertFlow(JimmerOpenApiConfig.Flow flow) {
        if (flow.authorizationUrl().isEmpty() && flow.tokenUrl().isEmpty() && flow.refreshUrl().isEmpty()
                && flow.scopes().isEmpty()) {
            return null;
        }
        return new OpenApiProperties.Flow(flow.authorizationUrl().orElse(null), flow.tokenUrl().orElse(null),
                flow.refreshUrl().orElse(null), new LinkedHashMap<>(flow.scopes()));
    }
}
