package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.DefaultExecutor;
import org.babyfish.jimmer.sql.runtime.ExecutionPurpose;
import org.babyfish.jimmer.sql.runtime.Executor;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterAssociatedIdsHandler;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsHandler;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkiverse.jimmer.test.http.model.HttpBook;
import io.quarkiverse.jimmer.test.http.model.HttpBookFetcher;
import io.quarkiverse.jimmer.test.http.model.HttpStore;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusUnitTest;

class MicroServiceHandlersTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(HttpBook.class.getPackage())
                    .addClasses(Counters.class, RequestState.class, MapperProducer.class, RequestCheckingExecutor.class,
                            HttpTestResponse.class)
                    .addAsResource(new StringAsset(HttpBook.class.getName() + "\n" + HttpStore.class.getName() + "\n"),
                            Constant.ENTITIES_RESOURCE))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:http-handlers;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "http-test");

    @Inject
    MicroServiceExporterIdsHandler ids;

    @Inject
    MicroServiceExporterAssociatedIdsHandler associatedIds;

    @Inject
    DataSource dataSource;

    @Inject
    Counters counters;

    @BeforeEach
    void prepare() throws SQLException {
        assertFalse(Arc.container().requestContext().isActive());
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table if not exists HTTP_STORE (ID bigint primary key, NAME varchar(100) not null)");
            statement.execute(
                    "create table if not exists HTTP_BOOK (ID bigint primary key, NAME varchar(100) not null, STORE_ID bigint not null)");
            statement.execute("delete from HTTP_BOOK");
            statement.execute("delete from HTTP_STORE");
            statement.execute("insert into HTTP_STORE values (10, 'Store')");
            statement.execute("insert into HTTP_BOOK values (1, 'Alpha', 10)");
        }
        counters.reset();
    }

    @Test
    void activatesBeforeDependencyInitializationAndDatabaseQueriesForBothHandlers() {
        HttpTestResponse first = new HttpTestResponse();
        ids.handle(first.context(parameters()));
        assertTrue(first.body.toString().contains("Alpha"));
        assertEquals(1, counters.created.get());
        assertEquals(1, counters.destroyed.get());
        assertEquals(1, counters.mappersCreated.get());
        assertEquals(1, counters.mappersDisposed.get());
        assertFalse(Arc.container().requestContext().isActive());

        HttpTestResponse second = new HttpTestResponse();
        associatedIds.handle(second.context(Map.of(Constant.FETCHER, fetcher(), Constant.PROP, "store",
                Constant.TARGET_IDS, "[10]")));
        assertTrue(second.body.toString().contains("Alpha"));
        assertEquals(2, counters.created.get());
        assertEquals(2, counters.destroyed.get());
        assertEquals(2, counters.mappersDisposed.get());
        assertTrue(counters.queries.get() >= 2);
        assertFalse(Arc.container().requestContext().isActive());
    }

    @Test
    void preservesAnExistingRequestContextAndItsBeans() {
        ManagedContext context = Arc.container().requestContext();
        context.activate();
        try {
            ids.handle(new HttpTestResponse().context(parameters()));
            ids.handle(new HttpTestResponse().context(parameters()));
            assertTrue(context.isActive());
            assertEquals(1, counters.created.get());
            assertEquals(0, counters.destroyed.get());
            assertEquals(1, counters.mappersCreated.get());
            assertEquals(0, counters.mappersDisposed.get());
        } finally {
            context.terminate();
        }
        assertEquals(1, counters.destroyed.get());
        assertEquals(1, counters.mappersDisposed.get());
    }

    @Test
    void cleansUpAnOwnedContextWhenDatabaseWorkFails() {
        counters.failQuery = true;
        assertThrows(RuntimeException.class, () -> ids.handle(new HttpTestResponse().context(parameters())));
        assertTrue(counters.queries.get() > 0);
        assertEquals(1, counters.created.get());
        assertEquals(1, counters.destroyed.get());
        assertEquals(1, counters.mappersDisposed.get());
        assertFalse(Arc.container().requestContext().isActive());
    }

    private static Map<String, String> parameters() {
        return Map.of(Constant.FETCHER, fetcher(), Constant.IDS, "[1]");
    }

    private static String fetcher() {
        return HttpBookFetcher.$.allScalarFields().toString();
    }

    @Singleton
    public static class Counters {
        final AtomicInteger created = new AtomicInteger();
        final AtomicInteger destroyed = new AtomicInteger();
        final AtomicInteger mappersCreated = new AtomicInteger();
        final AtomicInteger mappersDisposed = new AtomicInteger();
        final AtomicInteger queries = new AtomicInteger();
        volatile boolean failQuery;

        void reset() {
            created.set(0);
            destroyed.set(0);
            mappersCreated.set(0);
            mappersDisposed.set(0);
            queries.set(0);
            failQuery = false;
        }
    }

    @RequestScoped
    public static class RequestState {
        @Inject
        Counters counters;

        @PostConstruct
        void created() {
            assertTrue(Arc.container().requestContext().isActive());
            counters.created.incrementAndGet();
        }

        void touch() {
            assertTrue(Arc.container().requestContext().isActive());
        }

        @PreDestroy
        void destroyed() {
            counters.destroyed.incrementAndGet();
        }
    }

    @Singleton
    public static class MapperProducer {
        @Produces
        @RequestScoped
        ObjectMapper mapper(RequestState state, Counters counters) {
            state.touch();
            counters.mappersCreated.incrementAndGet();
            return new ObjectMapper();
        }

        void dispose(@Disposes ObjectMapper mapper, Counters counters) {
            counters.mappersDisposed.incrementAndGet();
        }

        @Produces
        @Singleton
        @io.quarkus.agroal.DataSource("unrelated")
        ObjectMapper unrelatedMapper() {
            throw new AssertionError("A datasource-qualified mapper is not the default HTTP mapper");
        }

        @Produces
        @Singleton
        @io.quarkus.agroal.DataSource("unrelated")
        JSqlClient unrelatedClient() {
            throw new AssertionError("A datasource-qualified client is not the default exporter client");
        }
    }

    @Singleton
    public static class RequestCheckingExecutor implements Executor {
        @Inject
        RequestState state;

        @Inject
        Counters counters;

        @Override
        public <R> R execute(Args<R> args) {
            state.touch();
            counters.queries.incrementAndGet();
            if (counters.failQuery) {
                throw new IllegalStateException("Database callback failed");
            }
            return DefaultExecutor.INSTANCE.execute(args);
        }

        @Override
        public BatchContext executeBatch(Connection connection, String sql, ImmutableProp generatedIdProp,
                ExecutionPurpose purpose, JSqlClientImplementor client, boolean constraintViolationTranslatable) {
            state.touch();
            return DefaultExecutor.INSTANCE.executeBatch(connection, sql, generatedIdProp, purpose, client,
                    constraintViolationTranslatable);
        }
    }
}
