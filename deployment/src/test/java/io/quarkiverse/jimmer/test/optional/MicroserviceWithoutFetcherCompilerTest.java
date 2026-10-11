package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

class MicroserviceWithoutFetcherCompilerTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutAntlr())
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:microservice-without-fetcher-compiler")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "inventory")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("org.antlr:antlr4-runtime"), messages);
                assertTrue(messages.contains("quarkus.jimmer.micro-service-name"), messages);
                assertTrue(messages.contains("inbound microservice exporter"), messages);
                boolean configurationFailure = false;
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    assertFalse(cause instanceof LinkageError,
                            "Expected configuration validation, not a missing-class failure");
                    configurationFailure |= cause instanceof ConfigurationException;
                }
                assertTrue(configurationFailure, messages);
            });

    @Test
    void inboundExporterRequiresTheFetcherCompilerDependency() {
        fail("Expected a configuration failure before startup");
    }
}
