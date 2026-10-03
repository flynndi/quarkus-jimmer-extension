package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

class NativeSwaggerUiTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(archive -> {
                HttpFeatureTestSupport.addGroupedMetadata(archive);
                archive.addClass(SwaggerUiTestSupport.class);
            })
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-swagger-ui", Version.getVersion())))
            .addBuildChainCustomizer(SwaggerUiTestSupport::assertNoSmallRyeOpenApi)
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:native-swagger")
            .overrideConfigKey("quarkus.http.root-path", "/app")
            .overrideConfigKey("quarkus.http.non-application-root-path", "internal")
            .overrideConfigKey("quarkus.jimmer.client.uri-prefix", "/app")
            .overrideConfigKey("quarkus.jimmer.client.openapi.path", "/jimmer/openapi.yml")
            .overrideConfigKey("quarkus.swagger-ui.path", "/native/swagger")
            .overrideConfigKey("quarkus.swagger-ui.urls.public", "/jimmer/openapi.yml?groups=public")
            .overrideConfigKey("quarkus.swagger-ui.urls.admin", "/jimmer/openapi.yml?groups=admin")
            .overrideConfigKey("quarkus.swagger-ui.urls-primary-name", "public");

    @TestHTTPResource
    URL baseUrl;

    @Test
    void nativeUiServesAllLinkedAssetsAndLoadsTheSelectedJimmerGroup() throws Exception {
        String html = SwaggerUiTestSupport.assertUiAndAssets(baseUrl.toURI().resolve("/native/swagger"));
        SwaggerUiTestSupport.assertGroupedSpecifications(baseUrl.toURI(), html);
        assertEquals(200, HttpFeatureTestSupport.get(baseUrl.toURI().resolve("/app/http-feature/status")).statusCode());
        assertEquals(200, HttpFeatureTestSupport.get(baseUrl.toURI().resolve("/app/http-feature/administration")).statusCode());
    }

    @Test
    void standaloneUiDoesNotInstallTheSmallRyeScannerOrItsDefaultEndpoint() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("io.quarkus.smallrye.openapi.runtime.OpenApiDocumentService", false, loader));
        for (String path : List.of("/app/internal/openapi", "/q/openapi", "/openapi", "/openapi.html",
                "/jimmer-client/swagger-ui.css", "/jimmer-client/swagger-ui.js")) {
            assertEquals(404, HttpFeatureTestSupport.get(baseUrl.toURI().resolve(path)).statusCode(), path);
        }
    }
}
