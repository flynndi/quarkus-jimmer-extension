package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

class MissingKotlinSqlTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutKotlinSql())
            .withApplicationRoot(archive -> archive.addClass(OptionalIntegrationTestSupport.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:missing-kotlin-sql")
            .overrideConfigKey("quarkus.jimmer.language", "kotlin")
            .assertException(failure -> {
                String messages = OptionalIntegrationTestSupport.messages(failure);
                assertTrue(messages.contains("org.babyfish.jimmer:jimmer-sql-kotlin"), messages);
                assertTrue(messages.contains("quarkus.jimmer.language=kotlin"), messages);
                boolean configurationFailure = false;
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    assertFalse(cause instanceof LinkageError,
                            "Expected configuration validation, not a missing-class failure");
                    configurationFailure |= cause instanceof ConfigurationException;
                }
                assertTrue(configurationFailure, messages);
            });

    @Test
    void kotlinLanguageRequiresTheKotlinSqlDependency() {
        fail("Expected a configuration failure before startup");
    }
}
