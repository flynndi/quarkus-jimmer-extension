package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
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

import io.quarkiverse.jimmer.runtime.cloud.ExchangeRestClient;
import io.quarkiverse.jimmer.runtime.cloud.QuarkusExchange;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkiverse.jimmer.test.cloud.model.ExchangeBook;
import io.quarkiverse.jimmer.test.cloud.model.ExchangeBookFetcher;
import io.quarkiverse.jimmer.test.cloud.model.ExchangeStore;
import io.quarkiverse.jimmer.test.cloud.model.ExchangeStoreFetcher;
import io.quarkiverse.jimmer.test.http.model.HttpBook;
import io.quarkiverse.jimmer.test.http.model.HttpBookFetcher;
import io.quarkiverse.jimmer.test.http.model.HttpStore;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

class QuarkusExchangeTest {

    private static final int PORT = availablePort();
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final UUID STORE_ID = UUID.fromString("8c2b5d43-c23a-4a36-a23c-b6ddabafaa01");
    private static final String BOOK_ID = "book\"\\\n雪&?=,+/";

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(HttpBook.class.getPackage())
                    .addPackage(ExchangeBook.class.getPackage())
                    .addClasses(BlockingExecutor.class)
                    .addAsResource(new StringAsset(String.join("\n", HttpBook.class.getName(), HttpStore.class.getName(),
                            ExchangeBook.class.getName(), ExchangeStore.class.getName()) + "\n"), Constant.ENTITIES_RESOURCE))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:microservice-exchange;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.http.test-port", Integer.toString(PORT))
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "http-test")
            .overrideConfigKey("quarkus.rest-client.local.url", BASE_URL)
            .overrideConfigKey("quarkus.rest-client.alternate.url", BASE_URL);

    @Inject
    QuarkusExchange exchange;

    @Inject
    DataSource dataSource;

    @Inject
    BlockingExecutor executor;

    @BeforeEach
    void prepare() throws SQLException {
        Arc.container().instance(QuarkusExchange.class).destroy();
        executor.reset();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table if not exists HTTP_STORE (ID bigint primary key, NAME varchar(100) not null)");
            statement.execute(
                    "create table if not exists HTTP_BOOK (ID bigint primary key, NAME varchar(100) not null, STORE_ID bigint not null)");
            statement.execute("create table if not exists EXCHANGE_STORE (ID uuid primary key, NAME varchar(100) not null)");
            statement.execute(
                    "create table if not exists EXCHANGE_BOOK (ID varchar(100) primary key, NAME varchar(100) not null, STORE_ID uuid not null)");
            statement.execute("delete from HTTP_BOOK");
            statement.execute("delete from HTTP_STORE");
            statement.execute("delete from EXCHANGE_BOOK");
            statement.execute("delete from EXCHANGE_STORE");
            statement.execute("insert into HTTP_STORE values (10, 'Store')");
            statement.execute("insert into HTTP_BOOK values (1, 'Alpha', 10)");
            try (var insert = connection.prepareStatement("insert into EXCHANGE_STORE values (?, ?)")) {
                insert.setObject(1, STORE_ID);
                insert.setString(2, "UUID store");
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("insert into EXCHANGE_BOOK values (?, ?, ?)")) {
                insert.setString(1, BOOK_ID);
                insert.setString(2, "Escaped book");
                insert.setObject(3, STORE_ID);
                insert.executeUpdate();
            }
        }
    }

    @Test
    void bothExportersRoundTripEntitiesAndTypedTuplesOverHttp() throws Exception {
        List<ImmutableSpi> books = exchange.findByIds("local", List.of(1L), HttpBookFetcher.$.allScalarFields());
        assertEquals(1, books.size());
        assertEquals("Alpha", ((HttpBook) books.get(0)).name());

        List<Tuple2<Object, ImmutableSpi>> associated = exchange.findByAssociatedIds("local",
                ImmutableType.get(HttpBook.class).getProp("store"), List.of(10L), HttpBookFetcher.$.allScalarFields());
        assertEquals(1, associated.size());
        assertEquals(10L, associated.get(0).get_1());
        assertEquals(1L, ((HttpBook) associated.get(0).get_2()).id());
    }

    @Test
    void stringEscapesAndUuidIdsRemainJsonValuesInBothRequestForms() throws Exception {
        List<ImmutableSpi> books = exchange.findByIds("local", List.of(BOOK_ID), ExchangeBookFetcher.$.allScalarFields());
        assertEquals(1, books.size());
        assertEquals(BOOK_ID, ((ExchangeBook) books.get(0)).id());
        List<ImmutableSpi> stores = exchange.findByIds("local", List.of(STORE_ID), ExchangeStoreFetcher.$.allScalarFields());
        assertEquals(STORE_ID, ((ExchangeStore) stores.get(0)).id());

        List<Tuple2<Object, ImmutableSpi>> associated = exchange.findByAssociatedIds("local",
                ImmutableType.get(ExchangeBook.class).getProp("store"), List.of(STORE_ID),
                ExchangeBookFetcher.$.allScalarFields());
        assertEquals(1, associated.size());
        assertEquals(STORE_ID, associated.get(0).get_1());
        assertEquals(BOOK_ID, ((ExchangeBook) associated.get(0).get_2()).id());
    }

    @Test
    void reusesClientsByServiceAndClosesTheirTransportWhenCdiDestroysTheExchange() throws Exception {
        QuarkusExchange instance = ClientProxy.unwrap(exchange);
        exchange.findByIds("local", List.of(1L), HttpBookFetcher.$.allScalarFields());
        ExchangeRestClient first = clients(instance).get("local");
        exchange.findByAssociatedIds("local", ImmutableType.get(HttpBook.class).getProp("store"), List.of(10L),
                HttpBookFetcher.$.allScalarFields());
        assertSame(first, clients(instance).get("local"));
        exchange.findByIds("alternate", List.of(1L), HttpBookFetcher.$.allScalarFields());
        ExchangeRestClient alternate = clients(instance).get("alternate");
        assertNotSame(first, alternate);

        Arc.container().instance(QuarkusExchange.class).destroy();
        assertThrows(IllegalStateException.class,
                () -> first.findByIds("[1]", HttpBookFetcher.$.allScalarFields().toString()));
        assertThrows(IllegalStateException.class,
                () -> alternate.findByIds("[1]", HttpBookFetcher.$.allScalarFields().toString()));
        assertThrows(IllegalStateException.class,
                () -> instance.findByIds("local", List.of(1L), HttpBookFetcher.$.allScalarFields()));
    }

    @Test
    void destructionWaitsForAnInFlightExchangeBeforeClosingItsClient() throws Exception {
        QuarkusExchange instance = ClientProxy.unwrap(exchange);
        executor.blockNext.set(true);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var request = workers.submit(() -> instance.findByIds("local", List.of(1L), HttpBookFetcher.$.allScalarFields()));
            assertTrue(executor.entered.await(10, TimeUnit.SECONDS));
            ExchangeRestClient client = clients(instance).get("local");
            CountDownLatch destroying = new CountDownLatch(1);
            var destruction = workers.submit(() -> {
                destroying.countDown();
                Arc.container().instance(QuarkusExchange.class).destroy();
            });
            assertTrue(destroying.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> destruction.get(200, TimeUnit.MILLISECONDS));
            executor.release.countDown();
            assertEquals("Alpha", ((HttpBook) request.get(10, TimeUnit.SECONDS).get(0)).name());
            destruction.get(10, TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class,
                    () -> client.findByIds("[1]", HttpBookFetcher.$.allScalarFields().toString()));
        } finally {
            executor.release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void missingServiceConfigurationReportsTheServiceName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> exchange.findByIds("unconfigured", List.of(1L), HttpBookFetcher.$.allScalarFields()));
        assertTrue(error.getMessage().contains("unconfigured"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ExchangeRestClient> clients(QuarkusExchange instance) throws ReflectiveOperationException {
        // Retain the real generated clients so assertions verify transport closure, not just a lifecycle flag.
        Field field = QuarkusExchange.class.getDeclaredField("clients");
        field.setAccessible(true);
        return (Map<String, ExchangeRestClient>) field.get(instance);
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Singleton
    public static class BlockingExecutor implements Executor {
        final AtomicBoolean blockNext = new AtomicBoolean();
        volatile CountDownLatch entered = new CountDownLatch(1);
        volatile CountDownLatch release = new CountDownLatch(1);

        void reset() {
            blockNext.set(false);
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        @Override
        public <R> R execute(Args<R> args) {
            if (blockNext.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The test did not release the blocked HTTP query");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return DefaultExecutor.INSTANCE.execute(args);
        }

        @Override
        public BatchContext executeBatch(Connection connection, String sql, ImmutableProp generatedIdProp,
                ExecutionPurpose purpose, JSqlClientImplementor client, boolean constraintViolationTranslatable) {
            return DefaultExecutor.INSTANCE.executeBatch(connection, sql, generatedIdProp, purpose, client,
                    constraintViolationTranslatable);
        }
    }
}
