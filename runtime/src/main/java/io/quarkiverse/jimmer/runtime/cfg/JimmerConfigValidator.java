package io.quarkiverse.jimmer.runtime.cfg;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.fetcher.ReferenceFetchType;

import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.runtime.configuration.ConfigurationException;

/** Validates configuration without creating clients, extension points, or database connections. */
public final class JimmerConfigValidator {

    private JimmerConfigValidator() {
    }

    public static void validateBuildTime(JimmerBuildTimeConfig config) {
        if (!config.enable()) {
            return;
        }
        Problems problems = new Problems();
        if (!"java".equalsIgnoreCase(config.language()) && !"kotlin".equalsIgnoreCase(config.language())) {
            problems.add("quarkus.jimmer.language", "must be java or kotlin");
        }
        if (config.client().ts().indent() < 0) {
            problems.add("quarkus.jimmer.client.ts.indent", "must be greater than or equal to 0");
        }
        config.errorTranslator().ifPresent(translator -> {
            if (translator.httpStatus() < 100 || translator.httpStatus() > 599) {
                problems.add("quarkus.jimmer.error-translator.http-status", "must be between 100 and 599");
            }
            if (translator.debugInfoMaxStackTraceCount() < 0) {
                problems.add("quarkus.jimmer.error-translator.debug-info-max-stack-trace-count",
                        "must be greater than or equal to 0");
            }
        });
        JimmerOpenApiConfig openapi = config.client().openapi();
        config.client().ts().path().ifPresent(path -> validateRoutePath("quarkus.jimmer.client.ts.path", path, problems));
        openapi.path().ifPresent(path -> validateRoutePath("quarkus.jimmer.client.openapi.path", path, problems));
        validateSecuritySchemes(openapi.properties().components().securitySchemes(), problems);
        problems.throwIfAny();
    }

    private static void validateSecuritySchemes(Map<String, JimmerOpenApiConfig.SecurityScheme> schemes, Problems problems) {
        schemes.forEach((name, scheme) -> {
            String prefix = "quarkus.jimmer.client.openapi.properties.components.securitySchemes.\""
                    + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\".";
            switch (scheme.type().orElse("")) {
                case "apiKey" -> requireValue(prefix + "name", scheme.name(), "for an apiKey security scheme", problems);
                case "http" -> requireValue(prefix + "scheme", scheme.scheme(), "for an http security scheme", problems);
                case "openIdConnect" -> requireValue(prefix + "open-id-connect-url", scheme.openIdConnectUrl(),
                        "for an openIdConnect security scheme", problems);
                case "oauth2" -> {
                    JimmerOpenApiConfig.Flows flows = scheme.flows();
                    flows.implicit()
                            .ifPresent(flow -> validateOAuthFlow(prefix + "flows.implicit.", flow, true, false, problems));
                    flows.password()
                            .ifPresent(flow -> validateOAuthFlow(prefix + "flows.password.", flow, false, true, problems));
                    flows.clientCredentials().ifPresent(
                            flow -> validateOAuthFlow(prefix + "flows.clientCredentials.", flow, false, true, problems));
                    flows.authorizationCode().ifPresent(
                            flow -> validateOAuthFlow(prefix + "flows.authorizationCode.", flow, true, true, problems));
                }
                default -> problems.add(prefix + "type", "must be apiKey, http, oauth2, or openIdConnect");
            }
        });
    }

    private static void validateOAuthFlow(String prefix, JimmerOpenApiConfig.Flow flow, boolean authorizationRequired,
            boolean tokenRequired, Problems problems) {
        if (authorizationRequired) {
            requireValue(prefix + "authorizationUrl", flow.authorizationUrl(), "for this OAuth flow", problems);
        }
        if (tokenRequired) {
            requireValue(prefix + "tokenUrl", flow.tokenUrl(), "for this OAuth flow", problems);
        }
    }

    private static void requireValue(String key, Optional<String> value, String reason, Problems problems) {
        if (value.isEmpty() || value.get().isBlank()) {
            problems.add(key, "must be non-empty " + reason);
        }
    }

    private static void validateRoutePath(String key, String path, Problems problems) {
        try {
            URI uri = new URI(path);
            if (path.isBlank() || uri.isAbsolute() || uri.getRawAuthority() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                problems.add(key, "must be a non-empty relative or absolute route path, without a host, query, or fragment");
            }
        } catch (URISyntaxException ex) {
            problems.add(key, "must be a valid relative or absolute route path");
        }
    }

    public static void validateDataSources(Map<String, JimmerDataSourceRuntimeConfig> dataSources) {
        Problems problems = new Problems();
        dataSources.forEach((name, config) -> validateDataSource(name, config, problems));
        problems.throwIfAny();
    }

    public static void validateDataSource(String name, JimmerDataSourceRuntimeConfig config) {
        Problems problems = new Problems();
        validateDataSource(name, config, problems);
        problems.throwIfAny();
    }

    private static void validateDataSource(String name, JimmerDataSourceRuntimeConfig config, Problems problems) {
        String prefix = DataSourceUtil.isDefault(name) ? "quarkus.jimmer."
                : "quarkus.jimmer.\"" + name.replace("\"", "\\\"") + "\".";
        config.defaultBatchSize().ifPresent(value -> {
            if (value < 1) {
                problems.add(prefix + "default-batch-size", "must be greater than or equal to 1");
            }
        });
        config.defaultListBatchSize().ifPresent(value -> {
            if (value < 1) {
                problems.add(prefix + "default-list-batch-size", "must be greater than or equal to 1");
            }
        });
        config.offsetOptimizingThreshold().ifPresent(value -> {
            if (value < 0) {
                problems.add(prefix + "offset-optimizing-threshold", "must be greater than or equal to 0");
            }
        });
        if (config.maxCommandJoinCount() < 0 || config.maxCommandJoinCount() > 8) {
            problems.add(prefix + "max-command-join-count", "must be between 0 and 8");
        }
        if (config.defaultReferenceFetchType() == ReferenceFetchType.AUTO) {
            problems.add(prefix + "default-reference-fetch-type", "must not be AUTO for the default fetch type");
        }
        config.dialect().ifPresent(className -> validateDialect(prefix + "dialect", className, problems));
    }

    private static void validateDialect(String key, String className, Problems problems) {
        try {
            // Loading without initialization avoids running application static initializers during validation.
            Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            if (!Dialect.class.isAssignableFrom(type) || type.isInterface()
                    || Modifier.isAbstract(type.getModifiers()) || !Modifier.isPublic(type.getModifiers())) {
                problems.add(key, "must name a public, concrete Dialect implementation: " + className);
                return;
            }
            type.getConstructor();
        } catch (ClassNotFoundException | LinkageError ex) {
            problems.add(key, "cannot load dialect class: " + className);
        } catch (NoSuchMethodException ex) {
            problems.add(key, "dialect must have a public no-argument constructor: " + className);
        }
    }

    private static final class Problems {
        private final List<String> messages = new ArrayList<>();
        private final Set<String> keys = new LinkedHashSet<>();

        private void add(String key, String message) {
            keys.add(key);
            messages.add("  - " + key + " " + message);
        }

        private void throwIfAny() {
            if (!messages.isEmpty()) {
                throw new ConfigurationException("Invalid Jimmer configuration:\n" + String.join("\n", messages), keys);
            }
        }
    }
}
