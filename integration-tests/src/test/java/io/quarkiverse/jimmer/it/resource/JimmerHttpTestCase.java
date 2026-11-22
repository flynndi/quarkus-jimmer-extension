package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

@QuarkusTest
public class JimmerHttpTestCase {

    @Test
    void generatesDocumentsForRestResponseBodiesAndPrivateFetchers() throws IOException {
        String openApi = given().get("/jimmer-test/openapi.yml").then().statusCode(200).extract().asString();
        var paths = new YAMLMapper().readTree(openApi).path("paths");
        assertEquals("#/components/schemas/BookDetailView", paths.path("/bookResource/book/{id}/view")
                .at("/get/responses/200/content/application~1json/schema/$ref").asText());
        assertTrue(paths.path("/bookResource/book/{id}").has("get"));
        assertTrue(paths.path("/bookResource/books/page").has("get"));
        assertFalse(openApi.contains("RestResponse"));

        byte[] archive = given().get("/jimmer-test/typescript.zip").then().statusCode(200)
                .contentType("application/zip").extract().asByteArray();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().endsWith("/BookResources.ts")) {
                    String service = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(service.contains("getBookView"), service);
                    assertTrue(service.contains("BookDetailView"), service);
                    assertFalse(service.contains("RestResponse"), service);
                    return;
                }
            }
        }
        throw new AssertionError("BookResources.ts is missing from the generated TypeScript client");
    }

    @Test
    void resolvesGeneratedTablesForDynamicQueries() {
        request().queryParam("id", 1)
                .get("/diagnostics/dynamic-tables")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("tableTypesMatch", equalTo(true))
                .body("bookName", equalTo("Learning GraphQL"))
                .body("storeName", equalTo("O'REILLY"));
    }

    @Test
    void paginatesAndSortsImmutableEntities() {
        request().queryParam("size", 2)
                .get("/bookResource/books/page")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("totalRowCount", equalTo(6))
                .body("totalPageCount", equalTo(3))
                .body("rows.id", contains(11, 9))
                .body("rows[0].keySet()", containsInAnyOrder("id", "name"));

        request().queryParam("index", 1).queryParam("size", 2)
                .get("/bookResource/books/page")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("rows.id", contains(7, 5));
    }

    @Test
    void rejectsInvalidPageBounds() {
        request().queryParam("index", -1).get("/bookResource/books/page").then().statusCode(400);
        request().queryParam("size", 0).get("/bookResource/books/page").then().statusCode(400);
    }

    @Test
    void serializesFetcherAssociations() {
        request().get("/bookResource/book/1")
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
        request().get("/bookResource/book/1/view")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("id", equalTo(1))
                .body("name", equalTo("Learning GraphQL"))
                .body("store.name", equalTo("O'REILLY"))
                .body("authors.id", containsInAnyOrder(1, 2))
                .body("authors.gender", containsInAnyOrder("FEMALE", "MALE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/bookResource/book/3000000000",
            "/bookResource/book/3000000000/view"
    })
    void absentEntitiesProduceNoContent(String path) {
        request().get(path)
                .then().statusCode(204).body(emptyString());
    }

    @Test
    void unmappedRoutesProduceNotFound() {
        request().get("/bookResource/no-such-resource")
                .then().statusCode(404);
    }

    @Test
    void createsUpdatesAndLogicallyDeletesAUserRoleOnTheNamedDatasource() {
        String id = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String roleId = UUID.randomUUID().toString();
        String updatedRoleId = UUID.randomUUID().toString();
        try {
            request().body(Map.of("id", id, "userId", userId, "roleId", roleId))
                    .post("/userRoleResources")
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
                    .put("/userRoleResources")
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
        request().queryParam("id", id)
                .get("/userRoleResources/deleteReverseById")
                .then().statusCode(200).contentType(ContentType.JSON)
                .body("id", equalTo(id))
                .body("deleteFlag", equalTo(true));
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
