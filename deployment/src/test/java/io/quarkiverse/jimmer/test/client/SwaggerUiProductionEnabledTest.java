package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class SwaggerUiProductionEnabledTest {

    private static final SwaggerUiProductionTestSupport.Ports PORTS = SwaggerUiProductionTestSupport.availablePorts();

    @RegisterExtension
    static final QuarkusProdModeTest APP = SwaggerUiProductionTestSupport.application("jimmer-swagger-ui-enabled")
            .overrideConfigKey("quarkus.swagger-ui.always-include", "true")
            .overrideConfigKey("quarkus.swagger-ui.validator-url", "none")
            .overrideConfigKey("quarkus.http.host", "127.0.0.1")
            .overrideConfigKey("quarkus.http.port", String.valueOf(PORTS.http()))
            .overrideConfigKey("quarkus.http.root-path", "/api")
            .overrideConfigKey("quarkus.http.non-application-root-path", "internal")
            .overrideConfigKey("quarkus.management.enabled", "true")
            .overrideConfigKey("quarkus.management.host", "127.0.0.1")
            .overrideConfigKey("quarkus.management.port", String.valueOf(PORTS.management()))
            .overrideConfigKey("quarkus.management.root-path", "/manage")
            .overrideConfigKey("quarkus.jimmer.client.openapi.properties.servers[0].url", PORTS.httpOrigin() + "/api")
            .overrideConfigKey("quarkus.http.cors.enabled", "true")
            .overrideConfigKey("quarkus.http.cors.origins", PORTS.managementOrigin())
            .overrideConfigKey("quarkus.http.cors.methods", "GET")
            .setRuntimeProperties(Map.of("quarkus.http.host", "127.0.0.1",
                    "quarkus.http.port", String.valueOf(PORTS.http())));

    @ProdBuildResults
    ProdModeTestResults results;

    @Test
    void includedProductionUiUsesManagementAssetsAndTheMainHttpApi() throws Exception {
        var httpUri = SwaggerUiProductionTestSupport.baseUri(APP);
        assertEquals(URI.create(PORTS.httpOrigin()), httpUri);
        var managementUri = URI.create(PORTS.managementOrigin());
        var uiUri = managementUri.resolve(SwaggerUiProductionTestSupport.UI_PATH + "/index.html");
        var page = HttpFeatureTestSupport.get(uiUri);
        assertEquals(200, page.statusCode(), page.body());
        assertTrue(page.body().contains(SwaggerUiProductionTestSupport.SPEC_PATH), page.body());
        SwaggerUiTestSupport.assertUiAndAssets(uiUri);
        var documentUri = managementUri.resolve(SwaggerUiProductionTestSupport.SPEC_PATH);
        var document = HttpFeatureTestSupport.get(documentUri);
        assertEquals(200, document.statusCode(), document.body());
        assertTrue(document.body().contains("/http-feature/status"), document.body());
        assertTrue(document.body().contains(PORTS.httpOrigin() + "/api"), document.body());
        assertEquals(404, HttpFeatureTestSupport.get(httpUri.resolve(SwaggerUiProductionTestSupport.UI_PATH + "/index.html"))
                .statusCode());
        assertEquals(404, HttpFeatureTestSupport.get(httpUri.resolve(SwaggerUiProductionTestSupport.SPEC_PATH)).statusCode());

        URI apiUri = httpUri.resolve("/api/http-feature/status");
        var api = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(
                HttpRequest.newBuilder(apiUri).timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .header("Origin", PORTS.managementOrigin()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, api.statusCode(), api.body());
        assertEquals("\"ready\"", api.body());
        assertTrue(api.headers().firstValue("content-type").orElse("").startsWith("application/json"));
        assertEquals(PORTS.managementOrigin(), api.headers().firstValue("access-control-allow-origin").orElseThrow());
        assertTrue(SwaggerUiProductionTestSupport.hasPackagedAsset(results, "swagger-ui.css"));
        assertTrue(SwaggerUiProductionTestSupport.hasPackagedAsset(results, "swagger-ui-bundle.js"));
        SwaggerUiProductionTestSupport.preserveIfRequested(results, Map.of(
                "ui.url", uiUri.toString(), "document.url", documentUri.toString(), "api.url", apiUri.toString(),
                "http.port", String.valueOf(PORTS.http()), "management.port", String.valueOf(PORTS.management())));
    }
}
