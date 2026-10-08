package io.quarkiverse.jimmer.test

import io.quarkiverse.jimmer.runtime.repository.KRepository
import io.quarkiverse.jimmer.test.model.CdiBook
import io.quarkus.agroal.DataSource
import io.quarkus.runtime.StartupEvent
import io.quarkus.test.QuarkusUnitTest
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@Suppress("DEPRECATION")
class RepositoryKotlinStartupLifecycleTest {
    companion object {
        @JvmField
        @RegisterExtension
        val app: QuarkusUnitTest = RepositoryStartupTestSupport.application("kotlin",
            DefaultBooks::class.java, NamedBooks::class.java, StartupSchema::class.java)
    }

    @Inject
    lateinit var defaults: DefaultBooks

    @Inject
    @DataSource("books")
    lateinit var named: NamedBooks

    @Test
    fun constructingRepositoriesLeavesDatabaseValidationUntilClientUse() {
        assertEquals("Default", defaults.findById(1L).orElseThrow().name())
        assertEquals("Named", named.findById(1L).orElseThrow().name())
        assertNull(defaults.sql.javaClient.validateDatabase())
        assertNull(named.sql.javaClient.validateDatabase())
    }

    interface DefaultBooks : KRepository<CdiBook, Long>

    @DataSource("books")
    interface NamedBooks : KRepository<CdiBook, Long>

    @Singleton
    class StartupSchema {
        fun initialize(@Observes event: StartupEvent, defaults: DefaultBooks,
            @DataSource("books") named: NamedBooks, defaultDataSource: javax.sql.DataSource,
            @DataSource("books") namedDataSource: javax.sql.DataSource) {
            // Observer parameters construct both repositories before this body can initialize the schema.
            // Reading sql.javaClient in a repository constructor would run ERROR validation too early.
            RepositoryStartupTestSupport.createSchema(defaultDataSource, "Default")
            RepositoryStartupTestSupport.createSchema(namedDataSource, "Named")
        }
    }
}
