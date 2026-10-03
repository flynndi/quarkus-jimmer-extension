package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class InvalidHttpConfigurationTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.jimmer.client.ts.path", "https://example.org/download")
            .overrideConfigKey("quarkus.jimmer.client.openapi.ui-path", "docs")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("quarkus.jimmer.client.ts.path"), messages);
                assertTrue(messages.contains("quarkus.jimmer.client.openapi.ui-path"), messages);
                assertTrue(messages.contains("Invalid Jimmer configuration"), messages);
            });

    @Test
    void reportsAllConfigurationProblemsBeforeBuildingRoutes() {
        fail("Expected a configuration failure before route construction");
    }
}
