package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.client.CodeBasedExceptionAdvice;
import io.quarkiverse.jimmer.runtime.client.CodeBasedRuntimeExceptionAdvice;
import io.quarkiverse.jimmer.runtime.cloud.MicroServiceExporterIdsHandler;
import io.quarkiverse.jimmer.runtime.cloud.QuarkusExchange;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

class HttpFeaturesDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(HttpFeatureTestSupport::addClasses)
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:http-disabled")
            .overrideConfigKey("quarkus.jimmer.error-translator.disabled", "true")
            .overrideConfigKey("quarkus.jimmer.error-translator.http-status", "409");

    @TestHTTPResource
    URL baseUrl;

    @Test
    void configuredRestServerDoesNotEnableJimmerHttpFeatures() throws Exception {
        assertEquals(200, HttpFeatureTestSupport.get(baseUrl.toURI().resolve("/http-feature/status")).statusCode());
        for (String path : new String[] { "/openapi.yml", "/openapi.html", "/jimmer-client/swagger-ui.css",
                "/jimmer-client/swagger-ui.js", "/q/swagger-ui", "/q/swagger-ui/swagger-ui-bundle.js",
                Constant.BY_IDS, Constant.BY_ASSOCIATED_IDS }) {
            assertEquals(404, HttpFeatureTestSupport.get(baseUrl.toURI().resolve(path)).statusCode(), path);
        }
        assertFalse(Arc.container().instance(QuarkusExchange.class).isAvailable());
        assertFalse(Arc.container().instance(MicroServiceExporterIdsHandler.class).isAvailable());
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("io.quarkus.swaggerui.runtime.SwaggerUiRecorder", false, loader));
        assertNull(loader.getResource("META-INF/resources/openapi-ui/swagger-ui.css"));
        assertNull(loader.getResource("META-INF/jimmer/openapi/index.html.template"));
    }

    @Test
    void disabledMappersAreNotDiscoveredAsProviders() throws Exception {
        assertFalse(Arc.container().instance(CodeBasedExceptionAdvice.class).isAvailable());
        assertFalse(Arc.container().instance(CodeBasedRuntimeExceptionAdvice.class).isAvailable());
        assertEquals(500, HttpFeatureTestSupport.get(baseUrl.toURI().resolve("/http-feature/failure")).statusCode());
    }
}
