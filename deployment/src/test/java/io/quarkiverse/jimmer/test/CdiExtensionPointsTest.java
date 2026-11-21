package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.filter.Filter;
import org.babyfish.jimmer.sql.filter.FilterArgs;
import org.babyfish.jimmer.sql.meta.LogicalDeletedLongGenerator;
import org.babyfish.jimmer.sql.runtime.Customizer;
import org.babyfish.jimmer.sql.runtime.Initializer;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusAopProxyProvider;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookProps;
import io.quarkiverse.jimmer.test.model.deletion.ArchivedBook;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class CdiExtensionPointsTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(CdiBook.class.getPackage())
                    .addPackage(ArchivedBook.class.getPackage())
                    .addClasses(BookFilter.class, TransactionalCustomizer.class, TransactionalInitializer.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n" + ArchivedBook.class.getName() + "\n"),
                            "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cdi-extension-points;DB_CLOSE_DELAY=-1");

    @Inject
    JSqlClient client;

    @Inject
    DataSource dataSource;

    @Test
    void discoversUninjectedSpiBeansAndKeepsTheirInterceptors() {
        JSqlClientImplementor implementation = (JSqlClientImplementor) client;
        assertEquals(7, implementation.getDefaultBatchSize());
        assertNotNull(client.getFilters().getFilter(CdiBook.class));
        TransactionalInitializer initializer = Arc.container().instance(TransactionalInitializer.class).get();
        assertEquals(Status.STATUS_ACTIVE, initializer.status);
        var provider = new QuarkusAopProxyProvider();
        Object customizer = Arc.container().instance(TransactionalCustomizer.class).get();
        assertNotEquals(TransactionalCustomizer.class, customizer.getClass());
        assertEquals(TransactionalCustomizer.class, provider.getTargetClass(customizer));
        assertNotEquals(TransactionalInitializer.class, initializer.getClass());
        assertEquals(TransactionalInitializer.class, provider.getTargetClass(initializer));
    }

    @Test
    void convenienceFactoryUsesTheDefaultDataSourceName() {
        JSqlClientImplementor customClient = (JSqlClientImplementor) SqlClients.java(Arc.container());
        assertEquals(7, customClient.getDefaultBatchSize());
        assertNotNull(customClient.getConnectionManager());
    }

    @Test
    void logicalDeleteWritesTheMarkerFromTheInjectedGenerator() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table if not exists ARCHIVED_BOOK "
                    + "(ID bigint primary key, DELETED_MARKER bigint not null)");
            statement.executeUpdate("merge into ARCHIVED_BOOK key(ID) values (1, 0)");
            client.getEntities().deleteCommand(ArchivedBook.class, 1L).execute(connection);
            try (var result = statement.executeQuery("select DELETED_MARKER from ARCHIVED_BOOK where ID = 1")) {
                assertTrue(result.next());
                assertEquals(73L, result.getLong(1));
            }
        }
    }

    @Test
    void logicalDeleteFallsBackToJimmerForUnmanagedGenerators() throws Exception {
        var generators = ((JSqlClientImplementor) client).getGeneratorContext();
        var generator = generators.getLogicalDeletedValueGenerator(LogicalDeletedLongGenerator.class);
        assertEquals(LogicalDeletedLongGenerator.class, generator.getClass());
    }

    @Test
    void logicalDeleteReportsMissingAndIncorrectlyTypedBeanNames() {
        var generators = ((JSqlClientImplementor) client).getGeneratorContext();
        var missing = assertThrows(IllegalStateException.class,
                () -> generators.getLogicalDeletedValueGenerator("missingBookGenerator"));
        assertTrue(missing.getMessage().contains("missingBookGenerator"));

        var wrongType = assertThrows(IllegalStateException.class,
                () -> generators.getLogicalDeletedValueGenerator("bookMarkerSource"));
        assertTrue(wrongType.getMessage().contains("bookMarkerSource"));
        assertTrue(wrongType.getMessage().contains(ArchivedBook.MarkerSource.class.getName()));
    }

    // Deliberately no @Unremovable, @Named, or injection points referring to these beans.
    @ApplicationScoped
    public static class BookFilter implements Filter<CdiBookProps> {
        @Override
        public void filter(FilterArgs<CdiBookProps> args) {
            args.where(args.getTable().name().ne("hidden"));
        }
    }

    @ApplicationScoped
    public static class TransactionalCustomizer implements Customizer {
        @Inject
        TransactionManager transactionManager;

        @Override
        @Transactional
        public void customize(JSqlClient.Builder builder) throws Exception {
            assertEquals(Status.STATUS_ACTIVE, transactionManager.getStatus());
            builder.setDefaultBatchSize(7);
        }
    }

    @Singleton
    public static class TransactionalInitializer implements Initializer {
        @Inject
        TransactionManager transactionManager;
        int status;

        @Override
        @Transactional
        public void initialize(JSqlClient sqlClient) throws Exception {
            status = transactionManager.getStatus();
        }
    }
}
