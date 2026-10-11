package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.Specification;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.query.specification.JSpecification;
import org.babyfish.jimmer.sql.ast.query.specification.SpecificationArgs;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.babyfish.jimmer.sql.transaction.Propagation;
import org.babyfish.jimmer.sql.transaction.TxConnectionManager;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.devui.JimmerDevUIService;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.arc.Arc;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.test.QuarkusUnitTest;

@SuppressWarnings("deprecation")
class HeadlessCoreTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            withoutOptionalIntegrations())
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addClasses(OptionalIntegrationTestSupport.class, Books.class, NameSpecification.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:headless-core")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY")
            .overrideConfigKey("quarkus.jimmer.transaction-cache-operator-fixed-delay", "off");

    @Inject
    JSqlClient client;

    @Inject
    TransactionManager transactions;

    @Inject
    Books books;

    @Inject
    JimmerBuildTimeConfig buildConfig;

    @Inject
    JimmerRuntimeConfig runtimeConfig;

    @Test
    void dataAccessAndTransactionsWorkWithoutOptionalIntegrations() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String type : new String[] {
                "io.quarkus.vertx.http.runtime.VertxHttpRecorder",
                "io.quarkus.resteasy.reactive.server.runtime.QuarkusResteasyReactiveRequestContext",
                "io.quarkus.rest.client.reactive.QuarkusRestClientBuilder",
                "io.quarkus.scheduler.Scheduler",
                "io.quarkus.redis.datasource.RedisDataSource",
                "com.github.benmanes.caffeine.cache.Caffeine",
                "org.babyfish.jimmer.client.runtime.Metadata",
                "org.babyfish.jimmer.client.generator.ts.TypeScriptContext",
                "org.babyfish.jimmer.client.generator.openapi.OpenApiGenerator",
                "org.babyfish.jimmer.sql.kt.KSqlClient",
                "org.babyfish.jimmer.sql.kt.cfg.KSqlClientDsl",
                "org.babyfish.jimmer.kt.DslScope",
                "org.antlr.v4.runtime.Token",
                "org.quartz.Scheduler" }) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type, false, loader), type);
        }
        var diagnostics = new JimmerDevUIService(buildConfig, runtimeConfig);
        assertEquals("java", diagnostics.getClients().get("language"));
        var beforeInitialization = diagnostics.getClient("<default>");
        assertEquals("uninitialized", beforeInitialization.get("state"));
        assertNull(beforeInitialization.get("actual"));

        JSqlClientImplementor sqlClient = (JSqlClientImplementor) client;
        assertTrue(sqlClient.getDialect() instanceof H2Dialect);
        TxConnectionManager connections = (TxConnectionManager) sqlClient.getConnectionManager();
        int result = connections.executeTransaction(Propagation.REQUIRED, connection -> {
            try (var statement = connection.createStatement()) {
                assertEquals(Status.STATUS_ACTIVE, transactions.getStatus());
                statement.execute("create table CDI_BOOK (ID bigint primary key, NAME varchar(100) not null)");
                return statement.executeUpdate("insert into CDI_BOOK (ID, NAME) values (1, 'Headless')");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
        assertEquals(1, result);
        assertEquals("Headless", client.findById(CdiBook.class, 1L).name());
        assertEquals("Headless", books.findById(1L).orElseThrow().name());
        assertEquals(List.of(1L), books.findByIdGreaterThan(0L, new NameSpecification("Headless"))
                .stream().map(CdiBook::id).toList());
        assertTrue(books.findByIdGreaterThan(0L, new NameSpecification("Missing")).isEmpty());
        assertSame(client, Jimmer.getJSqlClient("<default>"));
        assertEquals("Headless", SqlClients.java(Arc.container()).findById(CdiBook.class, 1L).name());

        var afterInitialization = diagnostics.getClient("<default>");
        assertEquals("initialized", afterInitialization.get("state"));
        Map<?, ?> actual = assertInstanceOf(Map.class, afterInitialization.get("actual"));
        assertEquals(H2Dialect.class.getName(), actual.get("dialect"));
    }

    private static Set<ArtifactKey> withoutOptionalIntegrations() {
        return Stream.of(OptionalIntegrationTestSupport.withoutHttp(), OptionalIntegrationTestSupport.withoutScheduler(),
                OptionalIntegrationTestSupport.withoutCacheBackends(),
                OptionalIntegrationTestSupport.withoutClientGeneration(), OptionalIntegrationTestSupport.withoutKotlinSql(),
                OptionalIntegrationTestSupport.withoutAntlr()).flatMap(Set::stream).collect(Collectors.toSet());
    }

    public interface Books extends JRepository<CdiBook, Long> {
        List<CdiBook> findByIdGreaterThan(long id, Specification<CdiBook> specification);
    }

    public record NameSpecification(String name) implements JSpecification<CdiBook, Table<CdiBook>> {
        @Override
        public Class<CdiBook> entityType() {
            return CdiBook.class;
        }

        @Override
        public void applyTo(SpecificationArgs<CdiBook, Table<CdiBook>> args) {
            args.where(args.getTable().<String> get("name").eq(name));
        }
    }
}
