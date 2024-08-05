package io.quarkiverse.jimmer.it;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTestProfile;

public class IntegrationTestsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, List<String>>> securities = new ArrayList<>();
        Map<String, List<String>> security = new HashMap<>();
        security.put("tenantHeader", List.of("1"));
        security.put("OAuth2", List.of("2"));
        securities.add(security);
        String securitiesJson;
        try {
            securitiesJson = objectMapper.writeValueAsString(securities);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
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
                Map.entry("quarkus.jimmer.database-validation.mode", "NONE"),
                Map.entry("quarkus.jimmer.error-translator.disabled", "false"),
                Map.entry("quarkus.jimmer.error-translator.debug-info-supported", "true"),
                Map.entry("quarkus.jimmer.client.ts.path", "/Code/ts.zip"),
                Map.entry("quarkus.jimmer.client.openapi.path", "/openapi.yml"),
                Map.entry("quarkus.jimmer.client.openapi.ui-path", "/openapi.html"),
                Map.entry("quarkus.jimmer.client.openapi.properties.info.title", "Jimmer REST Example(Java)"),
                Map.entry("quarkus.jimmer.client.openapi.properties.info.description", "test"),
                Map.entry("quarkus.jimmer.client.openapi.properties.info.version", "test.version"),
                Map.entry("quarkus.jimmer.client.openapi.properties.securities", securitiesJson));
    }
}
