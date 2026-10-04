package io.quarkiverse.jimmer.test.optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkiverse.jimmer.test.http.model.HttpBook;
import io.quarkiverse.jimmer.test.http.model.HttpBookFetcher;
import io.quarkiverse.jimmer.test.http.model.HttpStore;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

class MicroserviceExporterWithoutRestClientTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = OptionalIntegrationTestSupport.isolateExcludedDependencies(new QuarkusUnitTest(),
            OptionalIntegrationTestSupport.withoutRestClient())
            .withApplicationRoot(archive -> archive.addPackage(HttpBook.class.getPackage())
                    .addClasses(OptionalIntegrationTestSupport.class,
                            MicroserviceTestSupport.DefaultExchange.class)
                    .addAsResource(new StringAsset(HttpBook.class.getName() + "\n" + HttpStore.class.getName() + "\n"),
                            Constant.ENTITIES_RESOURCE))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:inbound-microservice")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "http-test");

    @TestHTTPResource
    URL baseUrl;

    @Test
    void exporterWorksWithCustomOutboundTransportAndNoRestClient() throws Exception {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.quarkus.rest.client.reactive.QuarkusRestClientBuilder", false,
                Thread.currentThread().getContextClassLoader()));
        String query = "?" + Constant.IDS + "=%5B%5D&" + Constant.FETCHER + "="
                + URLEncoder.encode(HttpBookFetcher.$.name().toString(), StandardCharsets.UTF_8);
        URI uri = baseUrl.toURI().resolve(Constant.BY_IDS + query);
        HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("[]", response.body());
    }
}
