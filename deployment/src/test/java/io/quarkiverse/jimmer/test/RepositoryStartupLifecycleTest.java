package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.SQLException;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.QuarkusUnitTest;

@SuppressWarnings("deprecation")
class RepositoryStartupLifecycleTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = RepositoryStartupTestSupport.application("java",
            DefaultBooks.class, NamedBooks.class, StartupSchema.class);

    @Inject
    DefaultBooks defaults;

    @Inject
    @DataSource("books")
    NamedBooks named;

    @Test
    void constructingRepositoriesLeavesDatabaseValidationUntilClientUse() {
        assertEquals("Default", defaults.findById(1L).orElseThrow().name());
        assertEquals("Named", named.findById(1L).orElseThrow().name());
        assertNull(defaults.sql().validateDatabase());
        assertNull(named.sql().validateDatabase());
    }

    public interface DefaultBooks extends JRepository<CdiBook, Long> {
    }

    @DataSource("books")
    public interface NamedBooks extends JRepository<CdiBook, Long> {
    }

    @Singleton
    public static class StartupSchema {

        void initialize(@Observes StartupEvent event, DefaultBooks defaults, @DataSource("books") NamedBooks named,
                javax.sql.DataSource defaultDataSource, @DataSource("books") javax.sql.DataSource namedDataSource)
                throws SQLException {
            // ArC constructs both singleton repositories before entering this observer. ERROR validation must
            // not run in their constructors, since this observer owns the application's schema initialization.
            assertNotNull(defaults);
            assertNotNull(named);
            RepositoryStartupTestSupport.createSchema(defaultDataSource, "Default");
            RepositoryStartupTestSupport.createSchema(namedDataSource, "Named");
        }
    }
}
