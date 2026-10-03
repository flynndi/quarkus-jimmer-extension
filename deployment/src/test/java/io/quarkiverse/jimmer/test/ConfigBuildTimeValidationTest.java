package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class ConfigBuildTimeValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest().withEmptyApplication()
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.jimmer.language", "scala")
            .overrideConfigKey("quarkus.jimmer.client.ts.indent", "-2")
            .assertException(failure -> {
                StringWriter diagnostic = new StringWriter();
                failure.printStackTrace(new PrintWriter(diagnostic));
                assertTrue(diagnostic.toString().contains("quarkus.jimmer.language"), diagnostic.toString());
                assertTrue(diagnostic.toString().contains("quarkus.jimmer.client.ts.indent"), diagnostic.toString());
            });

    @Test
    void invalidLanguageIsReportedEvenWithoutADatasourceOrALanguageBuildBranch() {
        fail("Invalid fixed configuration must prevent startup");
    }
}
