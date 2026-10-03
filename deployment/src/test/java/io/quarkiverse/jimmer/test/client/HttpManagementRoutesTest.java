package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

class HttpManagementRoutesTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .withApplicationRoot(HttpFeatureTestSupport::addMetadata)
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:http-management")
            .overrideConfigKey("quarkus.management.enabled", "true")
            .overrideConfigKey("quarkus.management.test-port", "0")
            .overrideConfigKey("quarkus.management.root-path", "/manage")
            .overrideConfigKey("quarkus.jimmer.client.openapi.path", "docs/spec.yml")
            .overrideConfigKey("quarkus.jimmer.client.ts.path", "docs/typescript.zip");

    @TestHTTPResource
    URL applicationUrl;

    @TestHTTPResource(management = true)
    URL managementUrl;

    @Test
    void documentsUseTheManagementServerAndResolvedRoot() throws Exception {
        assertEquals(404,
                HttpFeatureTestSupport.get(applicationUrl.toURI().resolve("/manage/docs/spec.yml")).statusCode());
        assertEquals(200,
                HttpFeatureTestSupport.get(managementUrl.toURI().resolve("/manage/docs/spec.yml")).statusCode());
        assertEquals(200,
                HttpFeatureTestSupport.get(managementUrl.toURI().resolve("/manage/docs/typescript.zip")).statusCode());
        assertEquals(404,
                HttpFeatureTestSupport.get(applicationUrl.toURI().resolve("/manage/docs/typescript.zip")).statusCode());
    }
}
