package io.quarkiverse.jimmer.test.devui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.runtime.Customizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusConnectionManager;
import io.quarkiverse.jimmer.runtime.devui.JimmerDevUIService;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class JimmerRuntimeDiagnosticsTest {
    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClass(ProbeCustomizer.class))
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:runtime-diagnostics")
            .overrideConfigKey("quarkus.datasource.password", "never-export-this-password")
            .overrideConfigKey("quarkus.datasource.disabled.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.disabled.active", "false")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY")
            .overrideConfigKey("quarkus.scheduler.enabled", "false")
            .overrideConfigKey("quarkus.jimmer.transaction-cache-operator-fixed-delay", "off");

    @Inject
    JimmerBuildTimeConfig buildConfig;
    @Inject
    JimmerRuntimeConfig runtimeConfig;
    @Inject
    JSqlClient client;

    @Test
    @SuppressWarnings("unchecked")
    void readsOnlyExistingInstancesAndDistinguishesConfigurationFromActualSettings() throws Exception {
        var diagnostics = new JimmerDevUIService(buildConfig, runtimeConfig);
        assertTrue(Arc.container().select(JimmerDevUIService.class).isUnsatisfied(),
                "Dev UI provider must not become an ordinary test/production bean");
        var clientsSnapshot = diagnostics.getClients();
        List<Map<String, Object>> clients = (List<Map<String, Object>>) clientsSnapshot.get("clients");
        assertEquals(Map.of("interval", "off", "intervalEnabled", false, "schedulerEnabled", false),
                clientsSnapshot.get("cacheRetry"));
        assertEquals(List.of("<default>", "disabled"), clients.stream().map(row -> row.get("name")).toList());
        assertEquals("uninitialized", clients.get(0).get("state"));
        assertEquals("inactive", clients.get(1).get("state"));
        assertNull(diagnostics.getClient("<default>").get("actual"));
        assertEquals("inactive", diagnostics.getClient("disabled").get("state"));
        assertEquals("unavailable", diagnostics.getClient("does-not-exist").get("state"));
        assertEquals(0, ProbeCustomizer.INVOCATIONS.get());

        // Only a normal application operation creates the client. Diagnostics must observe the resulting instance.
        client.getCaches();
        assertEquals(1, ProbeCustomizer.INVOCATIONS.get());
        var snapshot = diagnostics.getClient("<default>");
        assertEquals("initialized", snapshot.get("state"));
        Map<String, Object> configured = (Map<String, Object>) snapshot.get("configured");
        Map<String, Object> actual = (Map<String, Object>) snapshot.get("actual");
        assertEquals("auto", configured.get("dialect"));
        assertEquals(false, configured.get("mutationTransactionRequired"));
        assertEquals(H2Dialect.class.getName(), actual.get("dialect"));
        assertEquals(true, actual.get("mutationTransactionRequired"));
        assertEquals("TRANSACTION_ONLY", actual.get("triggerType"));
        assertEquals(true, actual.get("transactionCacheOperator"));
        assertEquals(QuarkusConnectionManager.class.getName(), actual.get("connectionManager"));
        assertEquals(true, actual.get("transactionCapable"));
        assertEquals(0, actual.get("objectCacheCount"));
        assertEquals(0, actual.get("propertyCacheCount"));
        assertEquals("initialized", ((List<Map<String, Object>>) diagnostics.getClients().get("clients")).get(0).get("state"));
        assertEquals(1, ProbeCustomizer.INVOCATIONS.get());
        String json = new ObjectMapper().writeValueAsString(snapshot);
        assertFalse(json.contains("jdbc:h2"));
        assertFalse(json.contains("never-export-this-password"));
    }

    @Singleton
    public static class ProbeCustomizer implements Customizer {
        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Override
        public void customize(JSqlClient.Builder builder) {
            INVOCATIONS.incrementAndGet();
            builder.setDialect(new H2Dialect());
            builder.setMutationTransactionRequired(true);
        }
    }
}
