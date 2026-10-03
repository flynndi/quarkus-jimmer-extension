package io.quarkiverse.jimmer.test

import io.quarkiverse.jimmer.runtime.repo.PageParam
import io.quarkiverse.jimmer.runtime.repo.support.AbstractKotlinRepository
import io.quarkiverse.jimmer.runtime.repository.KRepository
import io.quarkiverse.jimmer.runtime.repo.support.orderBy as repositoryOrderBy
import io.quarkiverse.jimmer.runtime.repository.orderBy as legacyOrderBy
import io.quarkiverse.jimmer.test.model.CdiBook
import io.quarkiverse.jimmer.test.model.CdiBookView
import io.quarkus.test.QuarkusUnitTest
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.meta.NullOrderMode
import org.babyfish.jimmer.sql.ast.query.OrderMode
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.eq
import org.babyfish.jimmer.sql.kt.ast.query.SortDsl
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.asset.StringAsset
import org.jboss.shrinkwrap.api.spec.JavaArchive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import javax.sql.DataSource

@Suppress("DEPRECATION")
class RepositoryKotlinTest {
    companion object {
        @JvmField
        @RegisterExtension
        val app: QuarkusUnitTest = QuarkusUnitTest()
            .setArchiveProducer {
                ShrinkWrap.create(JavaArchive::class.java)
                    .addPackage(CdiBook::class.java.`package`)
                    .apply {
                        // Reuse the Java entity's Draft/Fetcher metadata without its Java table wrappers.
                        // Jimmer's Kotlin DSL requires the raw table; KSP models do not generate TableEx classes.
                        content.keys.filter {
                            it.get().contains("/CdiBookTable") ||
                                it.get().endsWith("/Tables.class") || it.get().endsWith("/TableExes.class")
                        }.forEach { delete(it) }
                    }
                    .addClasses(BookRepository::class.java, LegacyBooks::class.java)
                    .addAsResource(StringAsset(CdiBook::class.java.name + "\n"), "META-INF/jimmer/entities")
            }
            .overrideConfigKey("quarkus.jimmer.language", "kotlin")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:kotlin-repository;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
    }

    @Inject
    lateinit var repository: BookRepository

    @Inject
    lateinit var legacy: LegacyBooks

    @Inject
    lateinit var dataSource: DataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create table if not exists CDI_BOOK (ID bigint primary key, NAME varchar(100) not null)")
                statement.executeUpdate("delete from CDI_BOOK")
                statement.executeUpdate("insert into CDI_BOOK(ID, NAME) values (1, 'Alpha'), (2, 'Beta'), (3, 'Gamma')")
            }
        }
    }

    @Test
    fun reifiedViewsAndPaginationUseTheNewRepositoryApi() {
        assertEquals("Alpha", repository.findView<CdiBookView>(1L)!!.name())
        assertNull(repository.findView<CdiBookView>(999L))
        assertEquals(setOf(1L, 3L), repository.findViews<CdiBookView>(listOf(1L, 3L)).map { it.id() }.toSet())
        assertEquals("Beta", repository.findMapView<CdiBookView>(listOf(2L))[2L]!!.name())
        assertEquals(listOf(3L, 2L, 1L), repository.findAllViews<CdiBookView>(descending()).map { it.id() })

        val page = repository.findPageView<CdiBookView>(PageParam.byIndex(0, 2), descending())
        assertEquals(listOf(3L, 2L), page.rows.map { it.id() })
        assertEquals(3L, page.totalRowCount)
        assertEquals(2L, page.totalPageCount)

        val entities = repository.findPage(PageParam.byIndex(1, 2), descending())
        assertEquals(listOf(1L), entities.rows.map { it.id() })
        val slice = repository.findSlice(2, 1, descending())
        assertEquals(listOf(2L, 1L), slice.rows.map { it.id() })
        assertFalse(slice.isHead)
        assertTrue(slice.isTail)
    }

    @Test
    fun protectedMutationHelpersExecuteInsideApplicationTransactions() {
        assertEquals(1, repository.rename(2L, "Delta"))
        assertEquals("Delta", repository.findView<CdiBookView>(2L)!!.name())
        assertEquals(1, repository.remove(1L))
        assertNull(repository.findView<CdiBookView>(1L))
    }

    @Test
    fun newAndLegacyTypedSortEntrypointsProduceTheSameOrder() {
        assertEquals(listOf(3L, 2L, 1L), repository.sortedWithNewHelper(descending()).map { it.id() })
        assertEquals(listOf(3L, 2L, 1L), repository.sortedWithLegacyHelper(descending()).map { it.id() })
    }

    @Test
    fun legacyRepositoryKeepsOptionalLookupAndDerivedQueries() {
        assertEquals("Alpha", legacy.findById(1L).orElseThrow().name())
        assertTrue(legacy.findById(999L).isEmpty)
        assertEquals(listOf(1L, 2L, 3L), legacy.findByNameLikeOrderById("a").map { it.id() })
    }

    @Test
    fun saveContractsRemainDistinctAndAcceptNonListIterables() {
        QuarkusTransaction.requiringNew().run {
            val entity = CdiBookView(4L, "Delta").toEntity()
            assertEquals(entity, legacy.save(entity))
            val batch = Iterable {
                listOf(CdiBookView(5L, "Epsilon").toEntity(), CdiBookView(6L, "Zeta").toEntity()).iterator()
            }
            assertFalse(batch is List<*>)
            assertEquals(listOf(5L, 6L), legacy.saveAll(batch).map { it.id() })

            val currentEntity = CdiBookView(7L, "Eta").toEntity()
            assertEquals(currentEntity, repository.save(currentEntity).modifiedEntity)
            val currentBatch = Iterable {
                listOf(CdiBookView(8L, "Theta").toEntity(), CdiBookView(9L, "Iota").toEntity()).iterator()
            }
            assertEquals(listOf(8L, 9L), repository.saveEntities(currentBatch).items.map { it.modifiedEntity.id() })
        }
        assertEquals("Delta", legacy.findById(4L).orElseThrow().name())
        assertEquals(setOf(5L, 6L), legacy.findByIds(listOf(5L, 6L)).map { it.id() }.toSet())
        assertEquals("Eta", repository.findById(7L)!!.name())
        assertEquals(setOf(8L, 9L), repository.findByIds(listOf(8L, 9L)).map { it.id() }.toSet())
    }

    private fun descending(): SortDsl<CdiBook>.() -> Unit = {
        // The fixture is a Java entity, so use Jimmer's immutable property metadata in the typed DSL.
        this += listOf(SortDsl.Order(ImmutableType.get(CdiBook::class.java).getProp("name"),
            OrderMode.DESC, NullOrderMode.NULLS_LAST))
    }

    interface LegacyBooks : KRepository<CdiBook, Long> {
        fun findByNameLikeOrderById(name: String): List<CdiBook>
    }

    @Singleton
    class BookRepository(sql: KSqlClient) : AbstractKotlinRepository<CdiBook, Long>(sql) {
        @Transactional
        fun rename(id: Long, name: String): Int = executeUpdate {
            set(table.get<String>("name") as KNonNullPropExpression<String>, name)
            where(table.get<Long>("id") eq id)
        }

        @Transactional
        fun remove(id: Long): Int = executeDelete {
            where(table.get<Long>("id") eq id)
        }

        fun sortedWithNewHelper(sort: SortDsl<CdiBook>.() -> Unit): List<CdiBook> = executeQuery {
            repositoryOrderBy(sort)
            select(table)
        }

        @Suppress("DEPRECATION")
        fun sortedWithLegacyHelper(sort: SortDsl<CdiBook>.() -> Unit): List<CdiBook> = executeQuery {
            legacyOrderBy(sort)
            select(table)
        }
    }
}
