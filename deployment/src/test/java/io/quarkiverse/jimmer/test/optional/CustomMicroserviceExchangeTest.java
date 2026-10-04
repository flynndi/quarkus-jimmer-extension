package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.babyfish.jimmer.sql.runtime.MicroServiceExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterAssociatedIdsHandler;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsHandler;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class CustomMicroserviceExchangeTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(MicroserviceTestSupport.DefaultExchange.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:custom-microservice")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "inventory");

    @Inject
    JSqlClient client;

    @Inject
    MicroServiceExchange exchange;

    @Test
    void customBeanOverridesDefaultHttpExchangeWhileHttpExporterRemainsAvailable() {
        assertEquals(MicroserviceTestSupport.DefaultExchange.class, exchange.getClass());
        assertSame(exchange, ((JSqlClientImplementor) client).getMicroServiceExchange());
        assertTrue(Arc.container().instance(MicroServiceExporterIdsHandler.class).isAvailable());
        assertTrue(Arc.container().instance(MicroServiceExporterAssociatedIdsHandler.class).isAvailable());
    }
}
