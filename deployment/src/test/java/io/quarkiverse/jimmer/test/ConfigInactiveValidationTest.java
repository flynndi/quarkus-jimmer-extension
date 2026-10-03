package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.DefaultDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

class ConfigInactiveValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClasses(UnusedDialect.class, Probe.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:config-active")
            .overrideConfigKey("quarkus.datasource.disabled.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.disabled.active", "false")
            .overrideConfigKey("quarkus.datasource.disabled.jdbc.url", "jdbc:h2:mem:config-inactive-datasource")
            .overrideConfigKey("quarkus.jimmer.disabled.default-batch-size", "0")
            .overrideConfigKey("quarkus.jimmer.disabled.dialect", "missing.Dialect")
            .overrideConfigKey("quarkus.datasource.optout.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.optout.jdbc.url", "jdbc:h2:mem:config-inactive-jimmer")
            .overrideConfigKey("quarkus.jimmer.optout.active", "false")
            .overrideConfigKey("quarkus.jimmer.optout.max-command-join-count", "9")
            .overrideConfigKey("quarkus.jimmer.transaction-cache-operator-fixed-delay", "disabled");

    @Test
    void inactiveSourceSemanticsDoNotPreventStartupOrInitializeTheActiveClient() {
        assertTrue(Arc.container().select(JSqlClient.class).getHandle().getBean().isActive());
        for (String name : new String[] { "disabled", "optout" }) {
            assertFalse(Arc.container().select(JSqlClient.class, new DataSourceLiteral(name)).getHandle().getBean().isActive());
        }
        assertEquals(0, Probe.instances);
    }

    @Test
    void manualClientsValidateInactiveSourceSettingsBeforeResolvingExtensionPoints() {
        ConfigurationException failure = assertThrows(ConfigurationException.class,
                () -> SqlClients.java(Arc.container(), null, "optout"));
        assertTrue(failure.getConfigKeys().contains("quarkus.jimmer.\"optout\".max-command-join-count"));
        assertEquals(0, Probe.instances);
    }

    @Singleton
    public static class UnusedDialect extends DefaultDialect {
        public UnusedDialect() {
            Probe.instances++;
            throw new AssertionError("Configuration validation must not initialize the client or its SPI beans");
        }
    }

    public static class Probe {
        public static int instances;
    }
}
