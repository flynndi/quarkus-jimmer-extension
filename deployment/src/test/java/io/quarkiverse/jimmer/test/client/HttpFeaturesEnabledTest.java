package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

class HttpFeaturesEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(HttpFeatureTestSupport::addMetadata)
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-rest-jackson", Version.getVersion())))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:http-enabled")
            .overrideConfigKey("quarkus.http.root-path", "/app")
            .overrideConfigKey("quarkus.http.non-application-root-path", "internal")
            .overrideConfigKey("quarkus.jimmer.client.openapi.path", "docs/spec.yml")
            .overrideConfigKey("quarkus.jimmer.client.ts.path", "docs/typescript.zip")
            .overrideConfigKey("quarkus.jimmer.error-translator.disabled", "false")
            .overrideConfigKey("quarkus.jimmer.error-translator.http-status", "409");

    @TestHTTPResource
    URL baseUrl;

    @Test
    void servesOnlyConfiguredDocumentsWithQuarkusRelativePathResolution() throws Exception {
        var spec = HttpFeatureTestSupport.get(baseUrl.toURI().resolve("/app/internal/docs/spec.yml"));
        assertEquals(200, spec.statusCode());
        assertTrue(spec.body().contains("/http-feature/status"));
        assertEquals(200,
                HttpFeatureTestSupport.get(baseUrl.toURI().resolve("/app/internal/docs/typescript.zip")).statusCode());
        assertEquals(404, HttpFeatureTestSupport.get(baseUrl.toURI().resolve("/openapi.yml")).statusCode());
        assertEquals(404, HttpFeatureTestSupport.get(baseUrl.toURI().resolve(Constant.BY_IDS)).statusCode());
    }

    @Test
    void explicitlyEnabledMapperWritesAJsonErrorResponse() throws Exception {
        var response = HttpFeatureTestSupport.get(baseUrl.toURI().resolve("/app/http-feature/failure"));
        assertEquals(409, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));
        var error = new ObjectMapper().readTree(response.body());
        assertEquals("HTTP_TEST", error.path("family").asText());
        assertEquals("EXPECTED_FAILURE", error.path("code").asText());
    }
}
