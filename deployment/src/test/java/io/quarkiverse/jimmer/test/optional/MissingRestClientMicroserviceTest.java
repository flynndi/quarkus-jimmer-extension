package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

class MissingRestClientMicroserviceTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = new QuarkusExtensionTest()
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .setExcludedDependencies(OptionalIntegrationTestSupport.withoutRestClient())
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:missing-rest-client")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "inventory")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("quarkus.jimmer.micro-service-name"), messages);
                assertTrue(messages.contains("quarkus-rest-client"), messages);
            });

    @Test
    void microserviceClientRequiresAnExchangeWhenTheHttpDefaultIsUnavailable() {
        fail("Expected a configuration failure before startup");
    }
}
