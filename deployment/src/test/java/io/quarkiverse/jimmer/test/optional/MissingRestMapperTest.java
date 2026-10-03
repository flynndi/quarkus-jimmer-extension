package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class MissingRestMapperTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .setExcludedDependencies(OptionalIntegrationTestSupport.withoutHttp())
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.jimmer.error-translator.disabled", "false")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("quarkus.jimmer.error-translator"), messages);
                assertTrue(messages.contains("quarkus-rest"), messages);
            });

    @Test
    void explicitExceptionTranslationRequiresRestEvenWithoutAMapperConsumer() {
        fail("Expected a configuration failure before startup");
    }
}
