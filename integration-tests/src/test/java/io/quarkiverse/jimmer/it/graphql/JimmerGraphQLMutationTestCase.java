package io.quarkiverse.jimmer.it.graphql;

import static io.restassured.RestAssured.given;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

@QuarkusTest
public class JimmerGraphQLMutationTestCase {

    @Test
    void testSaveBookMutation() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "query",
                        """
                                mutation {
                                  saveBook(input: {
                                    id: 9051,
                                    name: "graphql-save-book",
                                    edition: 1,
                                    price: 10.00,
                                    tenant: "graphql-test",
                                    storeId: 1,
                                    authors: [{
                                      firstName: "GraphQL",
                                      lastName: "Mutation",
                                      gender: MALE
                                    }]
                                  }) {
                                    id
                                    name
                                    edition
                                    price
                                    tenant
                                    store {
                                      id
                                      name
                                    }
                                    authors {
                                      firstName
                                      lastName
                                      gender
                                    }
                                  }
                                }
                                """))
                .when()
                .post("/graphql");

        JsonPath jsonPath = response.jsonPath();
        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertNull(jsonPath.get("errors"));
        Assertions.assertEquals(9051L, jsonPath.getLong("data.saveBook.id"));
        Assertions.assertEquals("graphql-save-book", jsonPath.getString("data.saveBook.name"));
        Assertions.assertEquals(1, jsonPath.getInt("data.saveBook.edition"));
        Assertions.assertEquals(10.0D, jsonPath.getDouble("data.saveBook.price"), 0.001D);
        Assertions.assertEquals("graphql-test", jsonPath.getString("data.saveBook.tenant"));
        Assertions.assertEquals(1L, jsonPath.getLong("data.saveBook.store.id"));
        Assertions.assertEquals("O'REILLY", jsonPath.getString("data.saveBook.store.name"));
        List<Map<String, Object>> authors = jsonPath.getList("data.saveBook.authors");
        Assertions.assertNotNull(authors);
        Assertions.assertEquals(1, authors.size());
        Assertions.assertEquals("GraphQL", authors.get(0).get("firstName"));
        Assertions.assertEquals("Mutation", authors.get(0).get("lastName"));
        Assertions.assertEquals("MALE", authors.get(0).get("gender"));
    }
}
