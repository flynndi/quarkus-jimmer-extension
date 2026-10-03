package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class ConfigRuntimeValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest().withEmptyApplication()
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:config-runtime-default")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:config-runtime-books")
            .overrideConfigKey("quarkus.jimmer.default-batch-size", "0")
            .overrideConfigKey("quarkus.jimmer.books.default-list-batch-size", "0")
            .overrideConfigKey("quarkus.jimmer.books.offset-optimizing-threshold", "-1")
            .overrideConfigKey("quarkus.jimmer.books.max-command-join-count", "9")
            .overrideConfigKey("quarkus.jimmer.books.default-reference-fetch-type", "AUTO")
            .overrideConfigKey("quarkus.jimmer.books.dialect", "java.lang.String")
            .assertException(failure -> {
                StringWriter diagnostic = new StringWriter();
                failure.printStackTrace(new PrintWriter(diagnostic));
                String message = diagnostic.toString();
                assertTrue(message.contains("quarkus.jimmer.default-batch-size"), message);
                for (String key : new String[] { "default-list-batch-size", "offset-optimizing-threshold",
                        "max-command-join-count", "default-reference-fetch-type", "dialect" }) {
                    assertTrue(message.contains("quarkus.jimmer.\"books\"." + key), message);
                }
            });

    @Test
    void invalidActiveSourcesAreReportedTogetherBeforeAClientIsUsed() {
        fail("Invalid runtime configuration must prevent startup");
    }
}
