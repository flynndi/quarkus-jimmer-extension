package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.Customizer;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.test.QuarkusDevModeTest;
import io.quarkus.test.common.http.TestHTTPResource;

public class JimmerDevModeTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TestHTTPResource
    URI baseUri;

    @RegisterExtension
    static final QuarkusDevModeTest devModeTest = new QuarkusDevModeTest()
            .withApplicationRoot(archive -> archive.addPackage(CdiBook.class.getPackage())
                    .addClasses(Books.class, NoClientInitialization.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n"), "META-INF/jimmer/entities")
                    .addAsResource(new StringAsset("""
                            quarkus.datasource.devservices.enabled=false
                            quarkus.redis.devservices.enabled=false
                            quarkus.datasource.db-kind=h2
                            quarkus.datasource.jdbc.url=jdbc:h2:mem:dev-ui-only
                            quarkus.datasource.password=dev-ui-must-not-export-this
                            quarkus.jimmer.client.openapi.path=jimmer/openapi.yml
                            quarkus.jimmer.client.ts.path=jimmer/client.zip
                            """), "application.properties"));

    @Test
    void servesTheDeclaredModelWithoutCreatingASqlClient() throws Exception {
        String html = get("/q/dev-ui/");
        var matcher = Pattern.compile("<script type=\"importmap\">(.*?)</script>", Pattern.DOTALL).matcher(html);
        assertTrue(matcher.find(), "Quarkus Dev UI must supply its import map");
        var imports = new ObjectMapper().readTree(matcher.group(1)).get("imports");
        String namespace = "quarkus-jimmer";
        assertTrue(imports.has(namespace + "-data"), imports::toPrettyString);
        String module = get(imports.get(namespace + "-data").asText());
        String json = module.substring(module.indexOf("export const jimmer = ") + "export const jimmer = ".length()).strip();
        if (json.endsWith(";")) {
            json = json.substring(0, json.length() - 1);
        }
        var data = new ObjectMapper().readTree(json);
        assertEquals(CdiBook.class.getName(), data.at("/entities/0/name").asText());
        assertEquals("id", data.at("/entities/0/properties/0/name").asText());
        assertEquals(Books.class.getName(), data.at("/overview/repositories/0/name").asText());
        assertEquals("<default>", data.at("/overview/repositories/0/dataSource").asText());
        assertEquals("h2", data.at("/overview/dataSources/0/dbKind").asText());
        assertFalse(module.contains("jdbc:h2"));
        assertFalse(module.contains("dev-ui-must-not-export-this"));

        // Quarkus serves the component modules and replaces their build-time-data import.
        for (String component : new String[] { "qwc-jimmer-overview.js", "qwc-jimmer-model.js" }) {
            String componentUrl = "/q/dev-ui/" + namespace + "/" + component;
            String source = get(componentUrl);
            assertTrue(source.contains(namespace + "-data"));
            assertFalse(source.contains("from 'build-time-data'"));
        }
    }

    private String get(String path) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), () -> path + "\n" + response.body());
        return response.body();
    }

    @Singleton
    public static class Books extends AbstractJavaRepository<CdiBook, Long> {
        public Books(JSqlClient sql) {
            super(sql);
        }
    }

    @Singleton
    public static class NoClientInitialization implements Customizer {
        @Override
        public void customize(JSqlClient.Builder builder) {
            throw new AssertionError("Browsing Dev UI must not initialize the SQL client");
        }
    }
}
