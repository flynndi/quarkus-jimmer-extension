package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsHandler;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class MicroServiceHandlerAmbiguousClientTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClasses(DuplicateClient.class, HttpTestResponse.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:http-ambiguous")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BINLOG_ONLY")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "http-test");

    @Inject
    MicroServiceExporterIdsHandler handler;

    @Test
    void rejectsAmbiguousDefaultClientsAndCleansUpItsContextBeforeQuerying() {
        assertFalse(Arc.container().requestContext().isActive());
        assertThrows(AmbiguousResolutionException.class,
                () -> handler.handle(new HttpTestResponse().context(Map.of())));
        assertFalse(Arc.container().requestContext().isActive());
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
