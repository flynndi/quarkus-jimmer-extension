package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class HeadlessMicroserviceTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutHttp())
            .withApplicationRoot(archive -> archive.addClasses(OptionalIntegrationTestSupport.class,
                    MicroserviceTestSupport.DefaultExchange.class,
                    MicroserviceTestSupport.NamedExchange.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:headless-microservice")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "inventory");

    @Inject
    JSqlClient client;

    @Test
    void customTransportUsesDatasourceQualifierAndWorksWithoutHttp() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("io.quarkus.rest.client.reactive.QuarkusRestClientBuilder", false, loader));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("io.quarkus.vertx.http.runtime.VertxHttpRecorder", false, loader));
        JSqlClientImplementor sqlClient = (JSqlClientImplementor) client;
        assertEquals("inventory", sqlClient.getMicroServiceName());
        assertInstanceOf(MicroserviceTestSupport.NamedExchange.class, sqlClient.getMicroServiceExchange());
        int result = sqlClient.getConnectionManager().execute(connection -> {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select 42")) {
                assertTrue(rows.next());
                return rows.getInt(1);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        assertEquals(42, result);
    }
}
