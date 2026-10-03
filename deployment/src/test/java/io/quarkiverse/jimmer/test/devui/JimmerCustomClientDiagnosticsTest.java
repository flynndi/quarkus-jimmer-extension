package io.quarkiverse.jimmer.test.devui;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.devui.JimmerDevUIService;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.test.QuarkusUnitTest;

class JimmerCustomClientDiagnosticsTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(ClientProducers.class))
            // With JDBC disabled, register Agroal's qualifier as metadata without copying its class into the application.
            .addBuildChainCustomizer(chain -> chain.addBuildStep(context -> context.produce(
                    AdditionalBeanBuildItem.builder().addBeanClass(DataSource.class).build()))
                    .produces(AdditionalBeanBuildItem.class).build())
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false");

    @Inject
    JimmerBuildTimeConfig buildConfig;

    @Inject
    JimmerRuntimeConfig runtimeConfig;

    @Test
    @SuppressWarnings("unchecked")
    void doesNotCreateCustomClientsResolveAmbiguityOrInvokeCustomMethods() {
        var diagnostics = new JimmerDevUIService(buildConfig, runtimeConfig);
        assertTrue(Arc.container().beanManager().isQualifier(DataSource.class));
        boolean requestContextActive = Arc.container().requestContext().isActive();
        List<Map<String, Object>> clients = (List<Map<String, Object>>) diagnostics.getClients().get("clients");
        assertEquals(List.of("ambiguous", "custom", "request"),
                clients.stream().map(client -> client.get("name")).toList());
        assertEquals("ambiguous", clients.get(0).get("state"));
        assertNull(clients.get(0).get("active"));
        assertEquals("uninitialized", clients.get(1).get("state"));
        assertEquals("unavailable", clients.get(2).get("state"));
        assertEquals("ambiguous", diagnostics.getClient("ambiguous").get("state"));
        assertNull(diagnostics.getClient("custom").get("actual"));
        assertEquals("unavailable", diagnostics.getClient("request").get("state"));
        assertEquals(requestContextActive, Arc.container().requestContext().isActive());
        assertEquals(0, ClientProducers.CUSTOM_CREATED.get());
        assertEquals(0, ClientProducers.AMBIGUOUS_CREATED.get());
        assertEquals(0, ClientProducers.REQUEST_CREATED.get());
        assertEquals(0, ClientProducers.METHOD_CALLS.get());

        // An application explicitly obtains its singleton; the diagnostics endpoint must only observe it afterward.
        JSqlClient client = Arc.container().select(JSqlClient.class, new DataSource.DataSourceLiteral("custom")).get();
        assertNotNull(client);
        assertTrue(Proxy.isProxyClass(client.getClass()));
        assertEquals(1, ClientProducers.CUSTOM_CREATED.get());

        Map<String, Object> snapshot = diagnostics.getClient("custom");
        assertEquals("initialized", snapshot.get("state"));
        assertEquals(true, snapshot.get("active"));
        assertNull(snapshot.get("actual"));
        assertTrue(snapshot.get("actualDetail").toString().contains("custom implementation"));
        clients = (List<Map<String, Object>>) diagnostics.getClients().get("clients");
        assertEquals("initialized", clients.get(1).get("state"));
        assertEquals("ambiguous", diagnostics.getClient("ambiguous").get("state"));
        assertEquals(1, ClientProducers.CUSTOM_CREATED.get());
        assertEquals(0, ClientProducers.AMBIGUOUS_CREATED.get());
        assertEquals(0, ClientProducers.REQUEST_CREATED.get());
        assertEquals(0, ClientProducers.METHOD_CALLS.get(), "Even Object methods on a custom client must remain untouched");
    }

    @Singleton
    public static class ClientProducers {

        static final AtomicInteger CUSTOM_CREATED = new AtomicInteger();
        static final AtomicInteger AMBIGUOUS_CREATED = new AtomicInteger();
        static final AtomicInteger REQUEST_CREATED = new AtomicInteger();
        static final AtomicInteger METHOD_CALLS = new AtomicInteger();

        @Produces
        @Singleton
        @Unremovable
        @DataSource("custom")
        JSqlClient custom() {
            CUSTOM_CREATED.incrementAndGet();
            return opaqueClient();
        }

        @Produces
        @Singleton
        @Unremovable
        @DataSource("ambiguous")
        JSqlClient firstAmbiguous() {
            AMBIGUOUS_CREATED.incrementAndGet();
            return opaqueClient();
        }

        @Produces
        @Singleton
        @Unremovable
        @DataSource("ambiguous")
        JSqlClient secondAmbiguous() {
            AMBIGUOUS_CREATED.incrementAndGet();
            return opaqueClient();
        }

        @Produces
        @RequestScoped
        @Unremovable
        @DataSource("request")
        JSqlClient requestScoped() {
            REQUEST_CREATED.incrementAndGet();
            return opaqueClient();
        }

        private static JSqlClient opaqueClient() {
            return (JSqlClient) Proxy.newProxyInstance(JSqlClient.class.getClassLoader(), new Class<?>[] { JSqlClient.class },
                    (proxy, method, arguments) -> {
                        METHOD_CALLS.incrementAndGet();
                        throw new AssertionError(
                                "Custom client methods must not be invoked by diagnostics: " + method.getName());
                    });
        }
    }
}
