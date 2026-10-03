package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.repo.JavaRepository;
import io.quarkiverse.jimmer.runtime.repo.PageParam;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookDraft;
import io.quarkiverse.jimmer.test.model.CdiBookFetcher;
import io.quarkiverse.jimmer.test.model.CdiBookProps;
import io.quarkiverse.jimmer.test.model.CdiBookView;
import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusUnitTest;

@SuppressWarnings("deprecation")
class RepositoryCompatibilityTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(CdiBook.class.getPackage())
                    .addClasses(CurrentBooks.class, LegacyBooks.class, UnimplementedBooks.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:repository-compatibility;DB_CLOSE_DELAY=-1");

    @Inject
    CurrentBooks current;

    @Inject
    LegacyBooks legacy;

    @Inject
    JSqlClient sql;

    @BeforeEach
    void prepareData() {
        ((JSqlClientImplementor) sql).getConnectionManager().execute(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("create table if not exists CDI_BOOK (ID bigint primary key, NAME varchar(100) not null)");
                statement.execute("delete from CDI_BOOK");
                statement.execute("insert into CDI_BOOK values (1, 'Alpha'), (2, 'Beta'), (3, 'Gamma')");
            } catch (java.sql.SQLException e) {
                throw new AssertionError(e);
            }
            return null;
        });
    }

    @Test
    void keepsDistinctLookupContractsAndSupportsFetcherAndViewQueries() {
        assertEquals("Alpha", current.findById(1L).name());
        assertEquals("Alpha", legacy.findById(1L).orElseThrow().name());
        assertNull(current.findById(99L));
        assertTrue(legacy.findById(99L).isEmpty());
        assertEquals("Beta", current.findById(2L, CdiBookFetcher.$.allScalarFields()).name());
        assertEquals(new CdiBookView(1, "Alpha"), current.findById(1L, CdiBookView.class));
        assertEquals(new CdiBookView(2, "Beta"), legacy.viewer(CdiBookView.class).findNullable(2L));
        assertEquals(new CdiBookView(1, "Alpha"), legacy.findByName("Alpha"));
        assertEquals(new CdiBookView(3, "Gamma"), current.findMapByIds(List.of(3L), CdiBookView.class).get(3L));
    }

    @Test
    void keepsSaveResultsAndAcceptsNonListIterables() {
        QuarkusTransaction.requiringNew().run(() -> {
            CdiBook newBook = book(4, "Delta");
            assertEquals(newBook, current.save(newBook).getModifiedEntity());
            CdiBook legacyBook = book(5, "Epsilon");
            assertEquals(legacyBook, legacy.save(legacyBook));

            Iterable<CdiBook> legacyBatch = () -> List.of(book(6, "Zeta"), book(7, "Eta")).iterator();
            assertEquals(List.of(6L, 7L), legacy.saveAll(legacyBatch).stream().map(CdiBook::id).toList());
            Iterable<CdiBook> currentBatch = () -> List.of(book(8, "Theta"), book(9, "Iota")).iterator();
            assertEquals(2, current.saveEntities(currentBatch).getItems().size());
        });
        assertEquals(9, current.findAll().size());
    }

    @Test
    void keepsLegacyDerivedQueriesAndAlignsPaging() {
        assertEquals(List.of(1L, 2L, 3L), legacy.findByNameLikeOrderById("a").stream().map(CdiBook::id).toList());
        Page<CdiBook> oldPage = legacy.findByNameLike("a", Pagination.of(0, 2));
        Page<CdiBook> newPage = current.findPage(PageParam.byIndex(0, 2), CdiBookProps.ID);
        assertEquals(3, oldPage.getTotalRowCount());
        assertEquals(3, newPage.getTotalRowCount());
        assertEquals(2, oldPage.getRows().size());
        assertEquals(List.of(1L, 2L), newPage.getRows().stream().map(CdiBook::id).toList());
        assertEquals(List.of(3L), current.findPage(PageParam.byNo(2, 2), CdiBookView.class, CdiBookProps.ID)
                .getRows().stream().map(CdiBookView::id).toList());
        assertFalse(current.findSlice(2, 0, CdiBookProps.ID).isTail());
    }

    @Test
    void newInterfacesDoNotOptIntoLegacyImplementationGeneration() {
        assertTrue(Arc.container().select(UnimplementedBooks.class).isUnsatisfied());
        assertNotEquals(LegacyBooks.class, legacy.getClass());
    }

    private static CdiBook book(long id, String name) {
        return CdiBookDraft.$.produce(draft -> draft.setId(id).setName(name));
    }

    @Singleton
    public static class CurrentBooks extends AbstractJavaRepository<CdiBook, Long> {
        public CurrentBooks(JSqlClient sql) {
            super(sql);
        }
    }

    public interface LegacyBooks extends JRepository<CdiBook, Long> {
        CdiBookView findByName(String name);

        List<CdiBook> findByNameLikeOrderById(String name);

        Page<CdiBook> findByNameLike(String name, Pagination pagination);
    }

    public interface UnimplementedBooks extends JavaRepository<CdiBook, Long> {
    }
}
