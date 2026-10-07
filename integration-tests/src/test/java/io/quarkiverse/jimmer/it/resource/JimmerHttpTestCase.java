package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@QuarkusTest
public class JimmerHttpTestCase {

    @Test
    void paginatesAndSortsImmutableEntities() {
        request().body(Map.of("index", 0, "size", 2))
                .post("/testResources/testBookRepositoryPageSort")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("totalRowCount", equalTo(6))
                .body("totalPageCount", equalTo(3))
                .body("rows.id", contains(11, 9));

        request().body(Map.of("index", 1, "size", 2))
                .post("/testResources/testBookRepositoryPageSort")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("rows.id", contains(7, 5));
    }

    @Test
    void serializesFetcherAssociations() {
        request().queryParam("id", 1)
                .get("/testResources/testBookRepositoryByIdFetcher")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("id", equalTo(1))
                .body("name", equalTo("Learning GraphQL"))
                .body("store.id", equalTo(1))
                .body("store.name", equalTo("O'REILLY"))
                .body("authors.id", containsInAnyOrder(1, 2))
                .body("authors.firstName", containsInAnyOrder("Eve", "Alex"));
    }

    @Test
    void serializesGeneratedDtoAssociations() {
        request().queryParam("id", 1)
                .get("/testResources/testBookRepositoryViewById")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("id", equalTo(1))
                .body("name", equalTo("Learning GraphQL"))
                .body("store.name", equalTo("O'REILLY"))
                .body("authors.id", containsInAnyOrder(1, 2))
                .body("authors.gender", containsInAnyOrder("FEMALE", "MALE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/testResources/testBookRepositoryByIdOptional",
            "/testResources/testBookRepositoryByIdFetcher",
            "/testResources/testBookRepositoryByIdFetcherOptional"
    })
    void absentEntitiesProduceNoContent(String path) {
        request().queryParam("id", 0).get(path)
                .then().statusCode(204).body(emptyString());
    }

    @Test
    void unmappedRoutesProduceNotFound() {
        request().get("/testResources/no-such-resource")
                .then().statusCode(404);
    }

    @Test
    void createsQueriesAndUpdatesGeneratedInputOnTheNamedDatasource() {
        String id = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String roleId = UUID.randomUUID().toString();
        String updatedRoleId = UUID.randomUUID().toString();
        try {
            request().body(Map.of("id", id, "userId", userId, "roleId", roleId))
                    .post("/testResources/testUserRoleRepositoryInsertInput")
                    .then().statusCode(200).contentType(ContentType.JSON)
                    .body("id", equalTo(id))
                    .body("userId", equalTo(userId))
                    .body("roleId", equalTo(roleId))
                    .body("deleteFlag", equalTo(false));

            request().queryParam("id", id)
                    .get("/userRoleResources/userRoleFindById")
                    .then().statusCode(200).contentType(ContentType.JSON)
                    .body("id", equalTo(id))
                    .body("userId", equalTo(userId))
                    .body("roleId", equalTo(roleId));

            request().queryParam("userId", userId).queryParam("roleId", roleId)
                    .get("/userRoleResources/testUserRoleSpecification")
                    .then().statusCode(200).contentType(ContentType.JSON)
                    .body("id", contains(id));

            request().body(Map.of("id", id, "userId", userId, "roleId", updatedRoleId))
                    .put("/testResources/testUserRoleRepositoryUpdateInput")
                    .then().statusCode(200);

            request().queryParam("id", id)
                    .get("/userRoleResources/userRoleFindById")
                    .then().statusCode(200).contentType(ContentType.JSON)
                    .body("id", equalTo(id))
                    .body("userId", equalTo(userId))
                    .body("roleId", equalTo(updatedRoleId));
        } finally {
            // This endpoint follows the entity's logical-delete contract.
            request().queryParam("id", id).delete("/userRoleResources/delete")
                    .then().statusCode(200);
        }

        request().queryParam("userId", userId).queryParam("roleId", updatedRoleId)
                .get("/userRoleResources/testUserRoleSpecification")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("", hasSize(0));
    }

    @Test
    void serializesRecursiveGeneratedDto() {
        request().get("/treeNodeResources/all")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("", hasSize(24))
                .body("find { it.id == 1 }.name", equalTo("Home"))
                .body("find { it.id == 1 }.childNodes.id", containsInAnyOrder(2, 9))
                .body("find { it.id == 1 }.childNodes.find { it.id == 9 }.childNodes.id", containsInAnyOrder(10, 18));
    }

    @Test
    void translatesGeneratedErrorsToJson() {
        request().get("/bookResource/testError")
                .then().statusCode(500).contentType(ContentType.JSON)
                .body("family", equalTo("USER_INFO"))
                .body("code", equalTo("ILLEGAL_USER_NAME"))
                .body("illegalChars", contains("a"));
    }

    private static RequestSpecification request() {
        return given().accept(ContentType.JSON).contentType(ContentType.JSON);
    }
}
