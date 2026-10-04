package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

class MissingClientGenerationTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutClientGeneration())
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.jimmer.client.openapi.path", "schema.yml")
            .overrideConfigKey("quarkus.jimmer.client.ts.path", "client.zip")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("org.babyfish.jimmer:jimmer-client"), messages);
                assertTrue(messages.contains("quarkus.jimmer.client.openapi.path"), messages);
                assertTrue(messages.contains("quarkus.jimmer.client.ts.path"), messages);
                boolean configurationFailure = false;
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    assertFalse(cause instanceof LinkageError,
                            "Expected configuration validation, not a missing-class failure");
                    configurationFailure |= cause instanceof ConfigurationException;
                }
                assertTrue(configurationFailure, messages);
            });

    @Test
    void configuredDocumentEndpointsRequireTheGenerationLibrary() {
        fail("Expected a configuration failure before startup");
    }
}
