package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.di.JLazyInitializationSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.kt.cfg.KInitializer;
import org.babyfish.jimmer.sql.runtime.Initializer;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.babyfish.jimmer.sql.runtime.ScalarProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;
import kotlin.Unit;

class ManualSqlClientFactoryTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addClasses(BuilderBeans.class,
                    TrackingJavaInitializer.class, TrackingKotlinInitializer.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:manual-factory-default;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:manual-factory-books;DB_CLOSE_DELAY=-1");

    @Inject
    TrackingJavaInitializer javaInitializer;

    @Inject
    TrackingKotlinInitializer kotlinInitializer;

    @Test
    void javaFactoryBuildsIndependentDefaultClientsBeforeReturning() {
        int before = javaInitializer.count;
        JSqlClient first = SqlClients.java(Arc.container(), builder -> builder.setDefaultBatchSize(5));
        assertEquals(before + 1, javaInitializer.count);
        assertSame(first, javaInitializer.lastClient);
        assertRealJavaClient(first);
        assertEquals(19, ((JSqlClientImplementor) first).getDefaultBatchSize());

        JSqlClient second = SqlClients.java(Arc.container(), null, null, builder -> builder.setDefaultBatchSize(7));
        assertEquals(before + 2, javaInitializer.count);
        assertSame(second, javaInitializer.lastClient);
        assertRealJavaClient(second);
        assertNotSame(first, second);
        assertEquals(19, ((JSqlClientImplementor) second).getDefaultBatchSize());
        assertTrue(databaseUrl(second).contains("manual-factory-default"));
    }

    @Test
    void kotlinFactoryBuildsIndependentDefaultClientsBeforeReturning() {
        int before = kotlinInitializer.count;
        KSqlClient first = SqlClients.kotlin(Arc.container(), dsl -> {
            dsl.getJavaBuilder().setDefaultBatchSize(5);
            return Unit.INSTANCE;
        });
        assertEquals(before + 1, kotlinInitializer.count);
        assertSame(first.getJavaClient(), kotlinInitializer.lastClient.getJavaClient());
        assertFalse(first instanceof ClientProxy);
        assertRealJavaClient(first.getJavaClient());
        assertEquals(19, first.getJavaClient().getDefaultBatchSize());

        KSqlClient second = SqlClients.kotlin(Arc.container(), null, null, dsl -> {
            dsl.getJavaBuilder().setDefaultBatchSize(7);
            return Unit.INSTANCE;
        });
        assertEquals(before + 2, kotlinInitializer.count);
        assertSame(second.getJavaClient(), kotlinInitializer.lastClient.getJavaClient());
        assertFalse(second instanceof ClientProxy);
        assertRealJavaClient(second.getJavaClient());
        assertNotSame(first, second);
        assertNotSame(first.getJavaClient(), second.getJavaClient());
        assertEquals(19, second.getJavaClient().getDefaultBatchSize());
        assertTrue(databaseUrl(second.getJavaClient()).contains("manual-factory-default"));
    }

    @Test
    void keepsDefaultAndNamedDatasourceConnectionsAndBuildersSeparate() {
        JSqlClient defaults = SqlClients.java(Arc.container());
        JSqlClient named = SqlClients.java(Arc.container(), null, "books");
        KSqlClient namedKotlin = SqlClients.kotlin(Arc.container(),
                AgroalDataSourceUtil.dataSourceInstance("books").get(), "books");

        assertTrue(databaseUrl(defaults).contains("manual-factory-default"));
        assertTrue(databaseUrl(named).contains("manual-factory-books"));
        assertTrue(databaseUrl(namedKotlin.getJavaClient()).contains("manual-factory-books"));
        assertEquals(19, ((JSqlClientImplementor) defaults).getDefaultBatchSize());
        assertEquals(23, ((JSqlClientImplementor) named).getDefaultBatchSize());
        assertEquals(23, namedKotlin.getJavaClient().getDefaultBatchSize());
    }

    private static void assertRealJavaClient(JSqlClient client) {
        assertFalse(client instanceof JLazyInitializationSqlClient);
        assertFalse(client instanceof ClientProxy);
    }

    private static String databaseUrl(JSqlClient client) {
        return ((JSqlClientImplementor) client).getConnectionManager().execute(connection -> {
            try {
                return connection.getMetaData().getURL();
            } catch (SQLException e) {
                throw new AssertionError(e);
            }
        });
    }

    @ApplicationScoped
    public static class BuilderBeans {
        @Produces
        ScalarProvider<String, String> optionalScalarProvider() {
            // A dependent producer may return null; optional SPI discovery must skip it.
            return null;
        }

        @Produces
        @Singleton
        Consumer<JSqlClient.Builder> defaultBuilder() {
            return builder -> builder.setDefaultBatchSize(19);
        }

        @Produces
        @Singleton
        @DataSource("books")
        Consumer<JSqlClient.Builder> booksBuilder() {
            return builder -> builder.setDefaultBatchSize(23);
        }
    }

    @Singleton
    public static class TrackingJavaInitializer implements Initializer {
        int count;
        JSqlClient lastClient;

        @Override
        public void initialize(JSqlClient sqlClient) {
            count++;
            lastClient = sqlClient;
        }
    }

    @Singleton
    public static class TrackingKotlinInitializer implements KInitializer {
        int count;
        KSqlClient lastClient;

        @Override
        public void initialize(KSqlClient sqlClient) {
            count++;
            lastClient = sqlClient;
        }
    }
}
