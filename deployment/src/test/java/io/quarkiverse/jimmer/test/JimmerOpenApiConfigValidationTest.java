package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.common.MapBackedConfigSource;

class JimmerOpenApiConfigValidationTest {

    private static final String SCHEMES = "quarkus.jimmer.client.openapi.properties.components.securitySchemes.";

    @Test
    void defaultsNeedNoSecurityConfiguration() {
        assertDoesNotThrow(() -> JimmerConfigValidator.validateBuildTime(config(Map.of())));
    }

    @Test
    void acceptsAllSecurityTypesAndFlowSpecificRequirements() {
        var config = config(Map.ofEntries(
                Map.entry(SCHEMES + "key.type", "apiKey"),
                Map.entry(SCHEMES + "key.name", "X-Api-Key"),
                Map.entry(SCHEMES + "http.type", "http"),
                Map.entry(SCHEMES + "http.scheme", "CustomAuthorization"),
                Map.entry(SCHEMES + "oidc.type", "openIdConnect"),
                Map.entry(SCHEMES + "oidc.open-id-connect-url", "/.well-known/openid-configuration"),
                Map.entry(SCHEMES + "oauth.type", "oauth2"),
                Map.entry(SCHEMES + "oauth.flows.implicit.authorizationUrl", "/authorize"),
                Map.entry(SCHEMES + "oauth.flows.password.tokenUrl", "/token"),
                Map.entry(SCHEMES + "oauth.flows.clientCredentials.tokenUrl", "/token"),
                Map.entry(SCHEMES + "oauth.flows.authorizationCode.authorizationUrl", "/authorize"),
                Map.entry(SCHEMES + "oauth.flows.authorizationCode.tokenUrl", "/token")));

        // Relative URLs, custom HTTP schemes, and empty scope maps are valid configuration.
        assertDoesNotThrow(() -> JimmerConfigValidator.validateBuildTime(config));
    }

    @Test
    void aggregatesMissingFieldsAndUnsupportedTypesWithPreciseKeys() {
        var config = config(Map.ofEntries(
                Map.entry(SCHEMES + "missing.description", "Missing type"),
                Map.entry(SCHEMES + "unsupported.type", "bearer"),
                Map.entry(SCHEMES + "key.type", "apiKey"),
                Map.entry(SCHEMES + "http.type", "http"),
                Map.entry(SCHEMES + "oidc.type", "openIdConnect"),
                Map.entry(SCHEMES + "oauth.type", "oauth2"),
                Map.entry(SCHEMES + "oauth.flows.implicit.refreshUrl", "/refresh"),
                Map.entry(SCHEMES + "oauth.flows.password.refreshUrl", "/refresh"),
                Map.entry(SCHEMES + "oauth.flows.clientCredentials.refreshUrl", "/refresh"),
                Map.entry(SCHEMES + "oauth.flows.authorizationCode.refreshUrl", "/refresh")));

        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> JimmerConfigValidator.validateBuildTime(config));
        assertEquals(Set.of(
                SCHEMES + "\"missing\".type",
                SCHEMES + "\"unsupported\".type",
                SCHEMES + "\"key\".name",
                SCHEMES + "\"http\".scheme",
                SCHEMES + "\"oidc\".open-id-connect-url",
                SCHEMES + "\"oauth\".flows.implicit.authorizationUrl",
                SCHEMES + "\"oauth\".flows.password.tokenUrl",
                SCHEMES + "\"oauth\".flows.clientCredentials.tokenUrl",
                SCHEMES + "\"oauth\".flows.authorizationCode.authorizationUrl",
                SCHEMES + "\"oauth\".flows.authorizationCode.tokenUrl"), failure.getConfigKeys());
        failure.getConfigKeys().forEach(key -> assertTrue(failure.getMessage().contains(key)));
    }

    @Test
    void keepsQuotedSchemeNamesInReportedConfigurationKeys() {
        var config = config(Map.of(SCHEMES + "\"company.token\".type", "apiKey"));

        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> JimmerConfigValidator.validateBuildTime(config));
        assertEquals(Set.of(SCHEMES + "\"company.token\".name"), failure.getConfigKeys());
    }

    @Test
    void disabledJimmerDoesNotApplySecuritySemantics() {
        var config = config(Map.of("quarkus.jimmer.enable", "false", SCHEMES + "token.type", "apiKey"));
        assertDoesNotThrow(() -> JimmerConfigValidator.validateBuildTime(config));
    }

    private static JimmerBuildTimeConfig config(Map<String, String> values) {
        return new SmallRyeConfigBuilder().addDefaultInterceptors().withMapping(JimmerBuildTimeConfig.class)
                .withSources(new MapBackedConfigSource("test", values) {
                }).build().getConfigMapping(JimmerBuildTimeConfig.class);
    }
}
