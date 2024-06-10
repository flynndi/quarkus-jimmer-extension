package io.quarkiverse.jimmer.it;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class IntegrationTestsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry("quarkus.application.name", "quarkus-jimmer-integration-tests-test"),
                Map.entry("quarkus.package.type", "uber-jar"),
                Map.entry("quarkus.datasource.db-kind", "h2"),
                Map.entry("quarkus.datasource.username", "default"),
                Map.entry("quarkus.datasource.jdbc.url", "jdbc:h2:mem:aaa"),
                Map.entry("quarkus.datasource.jdbc.min-size", "2"),
                Map.entry("quarkus.datasource.jdbc.max-size", "8"),
                Map.entry("quarkus.datasource.DB2.db-kind", "h2"),
                Map.entry("quarkus.datasource.DB2.username", "db2"),
                Map.entry("quarkus.datasource.DB2.jdbc.url", "jdbc:h2:mem:bd2"),
                Map.entry("quarkus.datasource.DB2.jdbc.min-size", "2"),
                Map.entry("quarkus.datasource.DB2.jdbc.max-size", "8"),
                Map.entry("quarkus.jimmer.language", "java"),
                Map.entry("quarkus.jimmer.show-sql", "true"),
                Map.entry("quarkus.jimmer.pretty-sql", "true"),
                Map.entry("quarkus.jimmer.inline-sql-variables", "true"),
                Map.entry("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY"),
                Map.entry("quarkus.jimmer.database-validation.mode", "NONE"));
    }
}
