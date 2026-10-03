package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class SwaggerUiProductionDefaultsTest {

    @RegisterExtension
    static final QuarkusProdModeTest APP = SwaggerUiProductionTestSupport.application("jimmer-swagger-ui-defaults");

    @ProdBuildResults
    ProdModeTestResults results;

    @Test
    void productionServesTheDocumentWithoutPackagingOrExposingSwaggerUiByDefault() throws Exception {
        var baseUri = SwaggerUiProductionTestSupport.baseUri(APP);
        var document = HttpFeatureTestSupport.get(baseUri.resolve(SwaggerUiProductionTestSupport.SPEC_PATH));
        assertEquals(200, document.statusCode(), document.body());
        assertTrue(document.body().contains("/http-feature/status"), document.body());
        for (String suffix : new String[] { "/", "/index.html", "/swagger-ui.css", "/swagger-ui-bundle.js" }) {
            var response = HttpFeatureTestSupport.get(baseUri.resolve(SwaggerUiProductionTestSupport.UI_PATH + suffix));
            assertEquals(404, response.statusCode(), suffix);
        }
        assertFalse(SwaggerUiProductionTestSupport.hasPackagedAsset(results, "swagger-ui.css"));
        assertFalse(SwaggerUiProductionTestSupport.hasPackagedAsset(results, "swagger-ui-bundle.js"));
    }
}
