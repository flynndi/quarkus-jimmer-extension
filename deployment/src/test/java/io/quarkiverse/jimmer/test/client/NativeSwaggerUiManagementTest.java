package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

class NativeSwaggerUiManagementTest {

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
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:native-swagger-management")
            .overrideConfigKey("quarkus.http.root-path", "/app")
            .overrideConfigKey("quarkus.http.non-application-root-path", "internal")
            .overrideConfigKey("quarkus.management.enabled", "true")
            .overrideConfigKey("quarkus.management.test-port", "0")
            .overrideConfigKey("quarkus.management.root-path", "/operations")
            .overrideConfigKey("quarkus.jimmer.client.uri-prefix", "/app")
            .overrideConfigKey("quarkus.jimmer.client.openapi.path", "/jimmer/openapi.yml")
            .overrideConfigKey("quarkus.swagger-ui.path", "/native/swagger")
            .overrideConfigKey("quarkus.swagger-ui.urls.public", "/jimmer/openapi.yml?groups=public")
            .overrideConfigKey("quarkus.swagger-ui.urls.admin", "/jimmer/openapi.yml?groups=admin")
            .overrideConfigKey("quarkus.swagger-ui.urls-primary-name", "public");

    @TestHTTPResource
    URL applicationUrl;

    @TestHTTPResource(management = true)
    URL managementUrl;

    @Test
    void absoluteUiAndSpecPathsUseTheManagementServerWithDifferentRootPaths() throws Exception {
        String html = SwaggerUiTestSupport.assertUiAndAssets(managementUrl.toURI().resolve("/native/swagger"));
        SwaggerUiTestSupport.assertGroupedSpecifications(managementUrl.toURI(), html);
        for (String path : List.of("/native/swagger", "/native/swagger/swagger-ui-bundle.js", "/jimmer/openapi.yml")) {
            assertEquals(404, HttpFeatureTestSupport.get(applicationUrl.toURI().resolve(path)).statusCode(), path);
        }
        assertEquals(404, HttpFeatureTestSupport.get(managementUrl.toURI().resolve("/operations/openapi")).statusCode());
        assertEquals(200,
                HttpFeatureTestSupport.get(applicationUrl.toURI().resolve("/app/http-feature/status")).statusCode());
    }
}
