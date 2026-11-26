package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

class AmbiguousMicroserviceExporterDataSourceTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = new QuarkusExtensionTest()
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:books")
            .overrideConfigKey("quarkus.datasource.stores.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.stores.jdbc.url", "jdbc:h2:mem:stores")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "inventory")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("Ambiguous JSqlClient"), messages);
                assertTrue(messages.contains("books"), messages);
                assertTrue(messages.contains("stores"), messages);
            });

    @Test
    void twoNamedDataSourcesRequireAnExplicitDefaultClient() {
        fail("Expected ambiguous exporter clients to prevent startup");
    }
}
