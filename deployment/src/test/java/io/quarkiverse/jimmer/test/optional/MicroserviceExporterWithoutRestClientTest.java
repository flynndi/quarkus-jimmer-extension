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
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkiverse.jimmer.test.http.model.HttpBook;
import io.quarkiverse.jimmer.test.http.model.HttpBookFetcher;
import io.quarkiverse.jimmer.test.http.model.HttpStore;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.test.common.http.TestHTTPResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MicroserviceExporterWithoutRestClientTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = OptionalIntegrationTestSupport
            .isolateExcludedDependencies(new QuarkusExtensionTest(),
                    withoutRestClientAndKotlinSql())
            .withApplicationRoot(archive -> archive.addPackage(HttpBook.class.getPackage())
                    .addClasses(OptionalIntegrationTestSupport.class,
                            MicroserviceTestSupport.DefaultExchange.class)
                    .addAsResource(new StringAsset(HttpBook.class.getName() + "\n" + HttpStore.class.getName() + "\n"),
                            Constant.ENTITIES_RESOURCE))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .overrideConfigKey("quarkus.datasource.books.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.books.jdbc.url", "jdbc:h2:mem:inbound-microservice;DB_CLOSE_DELAY=-1")
            .overrideConfigKey("quarkus.jimmer.micro-service-name", "http-test");

    @Inject
    @io.quarkus.agroal.DataSource("books")
    DataSource dataSource;

    @Inject
    JsonMapper mapper;

    @TestHTTPResource
    URL baseUrl;

    @Test
    void bothExportersUseTheOnlyNamedDataSourceWithoutARestClient() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "io.quarkus.rest.client.reactive.QuarkusRestClientBuilder", false, loader));
        assertThrows(ClassNotFoundException.class, () -> Class.forName(
                "org.babyfish.jimmer.sql.kt.KSqlClient", false, loader));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table HTTP_STORE (ID bigint primary key, NAME varchar(100) not null)");
            statement.execute("create table HTTP_BOOK (ID bigint primary key, NAME varchar(100) not null, STORE_ID bigint)");
            statement.executeUpdate("insert into HTTP_STORE values (10, 'Store')");
            statement.executeUpdate("insert into HTTP_BOOK values (1, 'Named book', 10)");
        }
        String fetcher = URLEncoder.encode(HttpBookFetcher.$.name().toString(), StandardCharsets.UTF_8);
        JsonNode books = get(Constant.BY_IDS + "?ids=%5B1%5D&fetcher=" + fetcher);
        assertEquals(mapper.readTree("[{\"id\":1,\"name\":\"Named book\"}]"), books);
        JsonNode associated = get(Constant.BY_ASSOCIATED_IDS + "?prop=store&targetIds=%5B10%5D&fetcher=" + fetcher);
        assertEquals(10, associated.path(0).path("_1").asInt());
        assertEquals(books.get(0), associated.path(0).path("_2"));
    }

    private JsonNode get(String path) throws Exception {
        URI uri = baseUrl.toURI().resolve(path);
        HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return mapper.readTree(response.body());
    }

    private static Set<ArtifactKey> withoutRestClientAndKotlinSql() {
        Set<ArtifactKey> excluded = new HashSet<>(OptionalIntegrationTestSupport.withoutRestClient());
        excluded.addAll(OptionalIntegrationTestSupport.withoutKotlinSql());
        return excluded;
    }
}
