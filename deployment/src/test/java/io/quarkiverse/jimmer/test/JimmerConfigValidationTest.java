package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.babyfish.jimmer.sql.dialect.DefaultDialect;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.smallrye.config.ConfigValidationException;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.common.MapBackedConfigSource;

class JimmerConfigValidationTest {

    @Test
    void aggregatesBuildTimeProblemsWithTheirConfigurationKeys() {
        var config = config(JimmerBuildTimeConfig.class, Map.of(
                "quarkus.jimmer.language", "scala",
                "quarkus.jimmer.client.ts.indent", "-1",
                "quarkus.jimmer.error-translator.http-status", "600",
                "quarkus.jimmer.error-translator.debug-info-max-stack-trace-count", "-1"));
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> JimmerConfigValidator.validateBuildTime(config));
        assertEquals(Set.of("quarkus.jimmer.language", "quarkus.jimmer.client.ts.indent",
                "quarkus.jimmer.error-translator.http-status",
                "quarkus.jimmer.error-translator.debug-info-max-stack-trace-count"), failure.getConfigKeys());
        failure.getConfigKeys().forEach(key -> assertTrue(failure.getMessage().contains(key)));
    }

    @Test
    void acceptsQuarkusRelativeRoutesWithoutImplicitEndpoints() {
        var defaults = config(JimmerBuildTimeConfig.class, Map.of());
        assertFalse(defaults.client().openapi().path().isPresent());
        assertFalse(defaults.client().ts().path().isPresent());
        var config = config(JimmerBuildTimeConfig.class, Map.of(
                "quarkus.jimmer.language", "KoTlIn",
                "quarkus.jimmer.client.ts.path", "generated/typescript",
                "quarkus.jimmer.client.ts.indent", "0",
                "quarkus.jimmer.client.openapi.path", "generated/spec.yml",
                "quarkus.jimmer.error-translator.http-status", "299",
                "quarkus.jimmer.error-translator.debug-info-max-stack-trace-count", "0"));
        assertDoesNotThrow(() -> JimmerConfigValidator.validateBuildTime(config));
        assertEquals("generated/spec.yml", config.client().openapi().path().orElseThrow());
    }

    @Test
    void aggregatesInvalidTypeScriptAndOpenApiRouteUris() {
        var config = config(JimmerBuildTimeConfig.class, Map.of(
                "quarkus.jimmer.client.ts.path", "https://example.org/download",
                "quarkus.jimmer.client.openapi.path", "spec.yml?version=1"));
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> JimmerConfigValidator.validateBuildTime(config));
        assertEquals(Set.of("quarkus.jimmer.client.ts.path", "quarkus.jimmer.client.openapi.path"), failure.getConfigKeys());
    }

    @Test
    void validatesJimmerBoundsWithoutInventingLoggingOrFetchDepthRestrictions() {
        var config = config(JimmerRuntimeConfig.class, Map.of(
                "quarkus.jimmer.default-batch-size", "1",
                "quarkus.jimmer.default-list-batch-size", "1",
                "quarkus.jimmer.offset-optimizing-threshold", "0",
                "quarkus.jimmer.max-command-join-count", "8",
                "quarkus.jimmer.max-join-fetch-depth", "-1",
                "quarkus.jimmer.pretty-sql", "true",
                "quarkus.jimmer.show-sql", "false",
                "quarkus.jimmer.transaction-cache-operator-fixed-delay", "off"));
        assertDoesNotThrow(() -> JimmerConfigValidator.validateDataSources(config.dataSources()));
        assertEquals("off", config.transactionCacheOperatorFixedDelay());
    }

    @Test
    void inactiveConfigurationStillRequiresValidMappingTypes() {
        ConfigValidationException failure = assertThrows(ConfigValidationException.class,
                () -> config(JimmerRuntimeConfig.class, Map.of(
                        "quarkus.jimmer.disabled.active", "false",
                        "quarkus.jimmer.disabled.default-batch-size", "not-a-number")));
        assertTrue(failure.getMessage().contains("default-batch-size"));
    }

    @Test
    void dialectValidationDoesNotInitializeOrConstructTheApplicationClass() {
        var config = config(JimmerRuntimeConfig.class,
                Map.of("quarkus.jimmer.dialect", DormantDialect.class.getName()));
        JimmerConfigValidator.validateDataSource(DataSourceUtil.DEFAULT_DATASOURCE_NAME,
                config.dataSources().get(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
        assertEquals(0, Probe.initializations);
    }

    private <T> T config(Class<T> type, Map<String, String> values) {
        return new SmallRyeConfigBuilder().addDefaultInterceptors().withMapping(type)
                .withSources(new MapBackedConfigSource("test", values) {
                }).build().getConfigMapping(type);
    }

    public static class DormantDialect extends DefaultDialect {
        static {
            Probe.initializations++;
        }

        public DormantDialect() {
            throw new AssertionError("Configuration validation must not construct the dialect");
        }
    }

    private static class Probe {
        private static int initializations;
    }
}
