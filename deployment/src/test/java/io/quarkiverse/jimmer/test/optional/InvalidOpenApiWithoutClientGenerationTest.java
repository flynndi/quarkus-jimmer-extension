package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class InvalidOpenApiWithoutClientGenerationTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutClientGeneration())
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.type", "http")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("Invalid Jimmer configuration"), messages);
                assertTrue(messages.contains(
                        "quarkus.jimmer.client.openapi.properties.components.securitySchemes.\"token\".scheme"), messages);
                assertFalse(messages.contains("NoClassDefFoundError"), messages);
                assertFalse(messages.contains("ClassNotFoundException"), messages);
            });

    @Test
    void validatesConfiguredSecurityWithoutLoadingTheGenerationLibrary() {
        fail("Expected build-time validation to report the missing HTTP security scheme");
    }
}
