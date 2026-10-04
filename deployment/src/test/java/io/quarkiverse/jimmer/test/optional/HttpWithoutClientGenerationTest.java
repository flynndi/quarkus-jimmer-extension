package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

class HttpWithoutClientGenerationTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutClientGeneration())
            .withApplicationRoot(archive -> archive.addClasses(OptionalIntegrationTestSupport.class, StatusResource.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:http-without-client")
            .overrideConfigKey("quarkus.jimmer.client.ts.null-render-mode", "NULL_OR_UNDEFINED")
            .overrideConfigKey("quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.type", "apiKey")
            .overrideConfigKey("quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.name", "token")
            .overrideConfigKey("quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.in", "COOKIE");

    @TestHTTPResource
    URL baseUrl;

    @Inject
    JimmerBuildTimeConfig config;

    @Test
    void httpAndClientConfigurationRemainUsableWithoutTheGenerationLibrary() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String type : new String[] { "org.babyfish.jimmer.client.runtime.Metadata",
                "org.babyfish.jimmer.client.generator.ts.TypeScriptContext",
                "org.babyfish.jimmer.client.generator.openapi.OpenApiGenerator" }) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(type, false, loader), type);
        }
        assertEquals("NULL_OR_UNDEFINED", config.client().ts().nullRenderMode().name());
        assertEquals("COOKIE", config.client().openapi().properties().components().securitySchemes().get("token").in().name());

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        for (String path : new String[] { "/status", "/openapi.yml", "/client.zip" }) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(baseUrl.toURI().resolve(path)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals("/status".equals(path) ? 200 : 404, response.statusCode(), path);
        }
    }

    @Path("/status")
    public static class StatusResource {

        @GET
        public String status() {
            return "ok";
        }
    }
}
