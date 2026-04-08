package io.quarkiverse.jimmer.it.graphql;

import static io.restassured.RestAssured.given;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

@QuarkusTest
public class JimmerGraphQLTestCase {

    @Test
    void testBookFacadeQuery() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "query",
                        """
                                query {
                                  book(id: 1) {
                                    id
                                    name
                                    edition
                                    store {
                                      id
                                      name
                                    }
                                    authors {
                                      firstName
                                      lastName
                                    }
                                  }
                                }
                                """))
                .when()
                .post("/graphql");

        JsonPath jsonPath = response.jsonPath();
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertNull(jsonPath.get("errors"));
        Assertions.assertEquals(1L, jsonPath.getLong("data.book.id"));
        Assertions.assertEquals("Learning GraphQL", jsonPath.getString("data.book.name"));
        Assertions.assertEquals(1, jsonPath.getInt("data.book.edition"));
        Assertions.assertEquals(1L, jsonPath.getLong("data.book.store.id"));
        Assertions.assertEquals("O'REILLY", jsonPath.getString("data.book.store.name"));
        List<Map<String, Object>> authors = jsonPath.getList("data.book.authors");
        Assertions.assertNotNull(authors);
        Assertions.assertEquals(2, authors.size());
        List<String> authorNames = authors.stream()
                .map(author -> String.valueOf(author.get("firstName")))
                .sorted()
                .collect(Collectors.toList());
        Assertions.assertEquals(List.of("Alex", "Eve"), authorNames);
    }

    @Test
    void testBookStoreTransientResolvers() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "query",
                        """
                                query {
                                  bookStore(id: 1) {
                                    id
                                    name
                                    avgPrice
                                    newestBooks {
                                      id
                                      name
                                      edition
                                    }
                                  }
                                }
                                """))
                .when()
                .post("/graphql");

        JsonPath jsonPath = response.jsonPath();
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertNull(jsonPath.get("errors"));
        Assertions.assertEquals(1L, jsonPath.getLong("data.bookStore.id"));
        Assertions.assertEquals("O'REILLY", jsonPath.getString("data.bookStore.name"));
        Assertions.assertEquals(53.1D, jsonPath.getDouble("data.bookStore.avgPrice"), 0.001D);
        List<Map<String, Object>> newestBooks = jsonPath.getList("data.bookStore.newestBooks");
        Assertions.assertNotNull(newestBooks);
        Assertions.assertEquals(3, newestBooks.size());
        Assertions.assertEquals("Learning GraphQL", newestBooks.get(0).get("name"));
        Assertions.assertEquals(3, newestBooks.get(0).get("edition"));
        Assertions.assertEquals("Effective TypeScript", newestBooks.get(1).get("name"));
        Assertions.assertEquals("Programming TypeScript", newestBooks.get(2).get("name"));
    }

    @Test
    void testBookStoresBatchResolvers() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "query",
                        """
                                query {
                                  bookStores(ids: [1, 2]) {
                                    id
                                    name
                                    avgPrice
                                    newestBooks {
                                      id
                                      name
                                      edition
                                      store {
                                        id
                                        name
                                      }
                                      authors {
                                        firstName
                                      }
                                    }
                                  }
                                }
                                """))
                .when()
                .post("/graphql");

        JsonPath jsonPath = response.jsonPath();
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertNull(jsonPath.get("errors"));

        List<Map<String, Object>> stores = jsonPath.getList("data.bookStores");
        Assertions.assertNotNull(stores);
        Assertions.assertEquals(2, stores.size());

        Map<Long, Map<String, Object>> storesById = stores.stream()
                .collect(Collectors.toMap(
                        store -> ((Number) store.get("id")).longValue(),
                        Function.identity()));

        Assertions.assertEquals("O'REILLY", storesById.get(1L).get("name"));
        Assertions.assertEquals(53.1D, ((Number) storesById.get(1L).get("avgPrice")).doubleValue(), 0.001D);
        List<Map<String, Object>> storeOneNewestBooks = castMaps(storesById.get(1L).get("newestBooks"));
        Assertions.assertEquals(3, storeOneNewestBooks.size());
        Assertions.assertEquals("Learning GraphQL", storeOneNewestBooks.get(0).get("name"));
        Assertions.assertEquals(3, storeOneNewestBooks.get(0).get("edition"));
        Assertions.assertEquals("O'REILLY", ((Map<?, ?>) storeOneNewestBooks.get(0).get("store")).get("name"));
        Assertions.assertEquals("Effective TypeScript", storeOneNewestBooks.get(1).get("name"));
        Assertions.assertEquals(2, storeOneNewestBooks.get(1).get("edition"));
        Assertions.assertEquals("Programming TypeScript", storeOneNewestBooks.get(2).get("name"));

        Assertions.assertEquals("MANNING", storesById.get(2L).get("name"));
        Assertions.assertEquals(81.0D, ((Number) storesById.get(2L).get("avgPrice")).doubleValue(), 0.01D);
        List<Map<String, Object>> storeTwoNewestBooks = castMaps(storesById.get(2L).get("newestBooks"));
        Assertions.assertEquals(1, storeTwoNewestBooks.size());
        Assertions.assertEquals("GraphQL in Action", storeTwoNewestBooks.get(0).get("name"));
        Assertions.assertEquals(2, storeTwoNewestBooks.get(0).get("edition"));
        Assertions.assertEquals("MANNING", ((Map<?, ?>) storeTwoNewestBooks.get(0).get("store")).get("name"));

        List<Map<String, Object>> authors = castMaps(storeTwoNewestBooks.get(0).get("authors"));
        Assertions.assertEquals(1, authors.size());
        Assertions.assertEquals("Samer", authors.get(0).get("firstName"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
