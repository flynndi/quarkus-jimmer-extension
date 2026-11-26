package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

class MicroServiceHandlerAmbiguousClientTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = new QuarkusExtensionTest()
            .withApplicationRoot(archive -> archive.addClass(DuplicateClient.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:http-ambiguous")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BINLOG_ONLY")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "http-test")
            .assertException(failure -> {
                StringBuilder messages = new StringBuilder();
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    assertFalse(cause instanceof AssertionError, cause::toString);
                    messages.append(cause.getMessage()).append('\n');
                }
                assertTrue(messages.toString().contains("Ambiguous JSqlClient"), messages::toString);
                assertTrue(messages.toString().contains("@Default"), messages::toString);
            });

    @Test
    void rejectsAmbiguousDefaultClientsDuringStartup() {
        fail("Expected ambiguous exporter clients to prevent startup");
    }

    @Singleton
    public static class DuplicateClient {
        @Produces
        @Singleton
        JSqlClient duplicate() {
            throw new AssertionError("An ambiguous SQL client must not be selected or instantiated");
        }
    }
}
