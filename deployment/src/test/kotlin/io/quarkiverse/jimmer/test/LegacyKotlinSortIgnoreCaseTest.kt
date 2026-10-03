package io.quarkiverse.jimmer.test

import io.quarkiverse.jimmer.runtime.repository.KRepository
import io.quarkiverse.jimmer.runtime.repository.common.Sort
import io.quarkiverse.jimmer.runtime.repository.orderBy as legacyOrderBy
import io.quarkiverse.jimmer.runtime.repository.orderByIf as legacyOrderByIf
import io.quarkiverse.jimmer.test.model.CdiBook
import io.quarkiverse.jimmer.test.model.CdiBookFetcher
import io.quarkiverse.jimmer.test.model.CdiBookView
import io.quarkus.test.QuarkusUnitTest
import jakarta.inject.Inject
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.jboss.shrinkwrap.api.spec.JavaArchive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import javax.sql.DataSource

@Suppress("DEPRECATION")
class LegacyKotlinSortIgnoreCaseTest {
    companion object {
        @JvmField
        @RegisterExtension
        val app: QuarkusUnitTest = QuarkusUnitTest()
            .setArchiveProducer {
                ShrinkWrap.create(JavaArchive::class.java)
                    .addPackage(CdiBook::class.java.`package`)
                    .apply {
                        // The Kotlin DSL needs raw tables rather than the Java fixture's TableEx wrappers.
                        content.keys.filter {
                            it.get().contains("/CdiBookTable") ||
                                it.get().endsWith("/Tables.class") || it.get().endsWith("/TableExes.class")
                        }.forEach { delete(it) }
                    }
                    .addClass(Books::class.java)
                    .addAsResource(StringAsset(CdiBook::class.java.name + "\n"), "META-INF/jimmer/entities")
            }
            .overrideConfigKey("quarkus.jimmer.language", "kotlin")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:legacy-kotlin-sort-ignore-case;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
    }

    @Inject
    lateinit var books: Books

    @Inject
    lateinit var sql: KSqlClient

    @Inject
    lateinit var dataSource: DataSource

    @BeforeEach
    fun prepareData() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create table if not exists CDI_BOOK (ID bigint primary key, NAME varchar(100) not null)")
                statement.execute("delete from CDI_BOOK")
                statement.execute("insert into CDI_BOOK values (1, 'alpha'), (2, 'Zulu'), (3, 'Beta'), (4, 'ALPHA')")
            }
        }
    }

    @Test
    fun legacyRepositorySortReachesListsPagesViewsAndDerivedQueries() {
        val sort = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.desc("id"))
        val expected = listOf(4L, 1L, 3L, 2L)
        assertEquals(listOf(4L, 3L, 2L, 1L), books.findAll(Sort.by("name")).map { it.id() })
        assertEquals(expected, books.findAll(sort).map { it.id() })
        assertEquals(expected, books.findAll(CdiBookFetcher.`$`.allScalarFields(), sort).map { it.id() })
        assertEquals(listOf(3L, 2L), books.findAll(1, 2, sort = sort).rows.map { it.id() })
        assertEquals(expected, books.viewer(CdiBookView::class).findAll(sort).map { it.id() })
        assertEquals(listOf(3L, 2L), books.viewer(CdiBookView::class).findAll(1, 2, sort).rows.map { it.id() })
        assertEquals(expected, books.findByIdGreaterThan(0L, sort).map { it.id() })
    }

    @Test
    fun legacyQueryExtensionsHonorIgnoreCaseAndTheirCondition() {
        val sort = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.desc("id"))
        val ordered = sql.createQuery(CdiBook::class) {
            legacyOrderBy(sort)
            select(table)
        }.execute()
        assertEquals(listOf(4L, 1L, 3L, 2L), ordered.map { it.id() })

        val conditionTrue = sql.createQuery(CdiBook::class) {
            legacyOrderByIf(true, sort)
            select(table)
        }.execute()
        assertEquals(listOf(4L, 1L, 3L, 2L), conditionTrue.map { it.id() })

        val conditionFalse = sql.createQuery(CdiBook::class) {
            legacyOrderByIf(false, sort)
            legacyOrderBy(Sort.by(Sort.Order.desc("id")))
            select(table)
        }.execute()
        assertEquals(listOf(4L, 3L, 2L, 1L), conditionFalse.map { it.id() })
    }

    interface Books : KRepository<CdiBook, Long> {
        fun findByIdGreaterThan(id: Long, sort: Sort): List<CdiBook>
    }
}
