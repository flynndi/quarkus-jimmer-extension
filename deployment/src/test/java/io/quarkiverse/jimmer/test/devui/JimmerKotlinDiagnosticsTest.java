package io.quarkiverse.jimmer.test.devui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.devui.JimmerDevUIService;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.QuarkusUnitTest;

class JimmerKotlinDiagnosticsTest {
    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.jimmer.language", "kotlin")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:kotlin-diagnostics");

    @Inject
    JimmerBuildTimeConfig buildConfig;
    @Inject
    JimmerRuntimeConfig runtimeConfig;
    @Inject
    @DataSource("books")
    KSqlClient client;

    @Test
    @SuppressWarnings("unchecked")
    void inspectsNamedKotlinClientsWithoutCreatingThemOrInventingADefaultClient() {
        var diagnostics = new JimmerDevUIService(buildConfig, runtimeConfig);
        var summary = diagnostics.getClients();
        assertEquals("kotlin", summary.get("language"));
        List<Map<String, Object>> clients = (List<Map<String, Object>>) summary.get("clients");
        assertEquals(List.of("books"), clients.stream().map(row -> row.get("name")).toList());
        assertEquals("uninitialized", diagnostics.getClient("books").get("state"));
        assertNull(diagnostics.getClient("books").get("actual"));
        client.getJavaClient().getCaches();
        var snapshot = diagnostics.getClient("books");
        assertEquals("initialized", snapshot.get("state"));
        var actual = (Map<String, Object>) snapshot.get("actual");
        assertEquals(H2Dialect.class.getName(), actual.get("dialect"));
        assertEquals("BINLOG_ONLY", actual.get("triggerType"));
        assertEquals(false, actual.get("transactionCacheOperator"));
    }
}
