package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class AmbiguousMicroserviceExchangeTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClasses(OptionalIntegrationTestSupport.class,
                    MicroserviceTestSupport.DefaultExchange.class,
                    MicroserviceTestSupport.OtherDefaultExchange.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:ambiguous-exchange")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "inventory")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("Ambiguous MicroServiceExchange"), messages);
                assertTrue(messages.contains("<default>"), messages);
            });

    @Test
    void ambiguityIsRejectedBeforeTheLazyClientIsInitialized() {
        fail("Expected a configuration failure before startup");
    }
}
