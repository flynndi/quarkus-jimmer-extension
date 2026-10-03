package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class MissingHttpDocsTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .setExcludedDependencies(OptionalIntegrationTestSupport.withoutHttp())
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.jimmer.client.openapi.path", "schema.yml")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("quarkus.jimmer.client.openapi.path"), messages);
                assertTrue(messages.contains("quarkus-vertx-http"), messages);
            });

    @Test
    void explicitDocumentEndpointRequiresHttp() {
        fail("Expected a configuration failure before startup");
    }
}
