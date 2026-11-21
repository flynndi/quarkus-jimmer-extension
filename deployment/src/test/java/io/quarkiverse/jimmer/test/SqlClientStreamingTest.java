package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.Predicate;
import org.babyfish.jimmer.sql.ast.query.ConfigurableRootQuery;
import org.babyfish.jimmer.sql.exception.ExecutionException;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookTable;
import io.quarkus.test.QuarkusUnitTest;

class SqlClientStreamingTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addAsResource(new StringAsset(CdiBook.class.getName()), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:streaming;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.jdbc.max-size", "1")
            .overrideConfigKey("quarkus.datasource.jdbc.acquisition-timeout", "2s")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "BINLOG_ONLY");

    private static final CdiBookTable BOOK = CdiBookTable.$;

    @Inject
    DataSource dataSource;

    @Inject
    JSqlClient sql;

    @Inject
    TransactionManager transactions;

    private ConfigurableRootQuery<CdiBookTable, Long> ids;

    @BeforeEach
    void prepareQuery() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table if not exists CDI_BOOK (ID bigint primary key, NAME varchar(100) not null)");
            statement.executeUpdate("merge into CDI_BOOK key(ID) values (1, 'First'), (2, 'Second')");
        }
        ids = sql.createQuery(BOOK).orderBy(BOOK.id()).select(BOOK.id());
    }

    @Test
    void closingStreamEarlyReturnsConnectionToPool() {
        try (var stream = ids.stream()) {
            assertEquals(1L, stream.findFirst().orElseThrow());
        }
        assertEquals(List.of(1L, 2L), ids.execute());
    }

    @Test
    void callerCanReuseExplicitConnectionAfterStreamCloses() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            try (var stream = ids.stream(connection)) {
                assertEquals(List.of(1L, 2L), stream.toList());
            }
            assertEquals(List.of(1L, 2L), ids.execute(connection));
        }
    }

    @Test
    void failedCursorCreationReturnsConnectionToPool() {
        var invalidQuery = sql.createQuery(BOOK).where(Predicate.sql("MISSING_COLUMN = 1")).select(BOOK.id());
        assertThrows(ExecutionException.class, invalidQuery::stream);
        assertEquals(List.of(1L, 2L), ids.execute());
    }

    @Test
    void streamParticipatesInTheCurrentTransaction() throws Exception {
        transactions.begin();
        try {
            sql.createDelete(BOOK).where(BOOK.id().eq(2L)).execute();
            try (var stream = ids.stream()) {
                assertEquals(List.of(1L), stream.toList());
            }
            assertEquals(List.of(1L), ids.execute());
        } finally {
            transactions.rollback();
        }
        assertEquals(List.of(1L, 2L), ids.execute());
    }
}
