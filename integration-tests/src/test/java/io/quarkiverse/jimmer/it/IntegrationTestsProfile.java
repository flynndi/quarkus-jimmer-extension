package io.quarkiverse.jimmer.it;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class IntegrationTestsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.db-kind", "h2",
                "quarkus.datasource.username", "default",
                "quarkus.datasource.jdbc.url", "jdbc:h2:mem:aaa",
                "quarkus.datasource.jdbc.min-size", "2",
                "quarkus.datasource.jdbc.max-size", "8",

                "quarkus.datasource.DB2.db-kind", "h2",
                "quarkus.datasource.DB2.username", "db2",
                "quarkus.datasource.DB2.jdbc.url", "jdbc:h2:mem:bd2",
                "quarkus.datasource.DB2.jdbc.min-size", "2",
                "quarkus.datasource.DB2.jdbc.max-size", "8");
    }
}
