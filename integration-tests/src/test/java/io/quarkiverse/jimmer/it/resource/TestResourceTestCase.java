package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;

import java.util.Arrays;
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.it.IntegrationTestsProfile;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkiverse.jimmer.it.repository.UserRoleRepository;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.Header;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.vertx.core.http.HttpHeaders;

@QuarkusTest
@TestProfile(IntegrationTestsProfile.class)
public class TestResourceTestCase {

    @Inject
    BookRepository bookRepository;

    @Inject
    @DataSource(Constant.DATASOURCE2)
    UserRoleRepository userRoleRepository;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testRepository() {
        BookRepository bookRepository = Arc.container().instance(BookRepository.class).get();
        BookStoreRepository bookStoreRepository = Arc.container().instance(BookStoreRepository.class).get();
        UserRoleRepository userRoleRepository = Arc.container()
                .instance(UserRoleRepository.class, new DataSource.DataSourceLiteral("DB2")).get();
        Assertions.assertNotNull(bookRepository);
        Assertions.assertNotNull(bookStoreRepository);
        Assertions.assertEquals(bookRepository, this.bookRepository);
        Assertions.assertEquals(userRoleRepository, this.userRoleRepository);
    }

    @Test
    void testPage() {
        String body;
        try {
            body = objectMapper.writeValueAsString(Pagination.of(0, 1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Response response = given()
                .body(body)
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryPage");
        JsonPath responseJsonPath = response.jsonPath();
        Assertions.assertEquals(6, responseJsonPath.getInt("totalRowCount"));
        Assertions.assertEquals(6, responseJsonPath.getInt("totalPageCount"));
    }

    @Test
    void testPageOther() {
        String body;
        try {
            body = objectMapper.writeValueAsString(Pagination.of(0, 1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Response response = given()
                .body(body)
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryPageOther");
        JsonPath responseJsonPath = response.jsonPath();
        Assertions.assertEquals(6, responseJsonPath.getInt("totalRowCount"));
        Assertions.assertEquals(6, responseJsonPath.getInt("totalPageCount"));
    }

    @Test
    void testBookRepositoryPageSort() {
        String body;
        try {
            body = objectMapper.writeValueAsString(Pagination.of(0, 1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Response response = given()
                .body(body)
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryPageSort");
        JsonPath responseJsonPath = response.jsonPath();
        Assertions.assertEquals(6, responseJsonPath.getInt("totalRowCount"));
        Assertions.assertEquals(6, responseJsonPath.getInt("totalPageCount"));
        Assertions.assertEquals(11, responseJsonPath.getLong("rows[0].id"));
    }

    @Test
    void testPageFetcher() {
        String body;
        try {
            body = objectMapper.writeValueAsString(Pagination.of(0, 1));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Response response = given()
                .body(body)
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryPageFetcher");
        JsonPath responseJsonPath = response.jsonPath();
        Assertions.assertEquals(6, responseJsonPath.getInt("totalRowCount"));
        Assertions.assertEquals(6, responseJsonPath.getInt("totalPageCount"));
        Assertions.assertNotNull(responseJsonPath.getList("rows"));
        Assertions.assertNotNull(responseJsonPath.get("rows.authors"));
    }

    @Test
    void testBookRepositoryById() {
        Response response = given()
                .queryParam("id", 1L)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryById");
        Assertions.assertNotNull(response.jsonPath());
    }

    @Test
    void testBookRepositoryByIdOptionalPresent() {
        Response response = given()
                .queryParam("id", 1L)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryByIdOptional");
        Assertions.assertNotNull(response.jsonPath());
    }

    @Test
    void testBookRepositoryByIdOptionalEmpty() {
        Response response = given()
                .queryParam("id", 0)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryByIdOptional");
        Assertions.assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());
    }

    @Test
    void testBookRepositoryByIdFetcher() {
        Response response = given()
                .queryParam("id", 0)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryByIdFetcher");
        Assertions.assertEquals(response.statusCode(), HttpStatus.SC_NO_CONTENT);
    }

    @Test
    void testBookRepositoryByIdFetcherOptionalPresent() {
        Response response = given()
                .queryParam("id", 1L)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryByIdFetcherOptional");
        Assertions.assertNotNull(response.jsonPath());
    }

    @Test
    void testBookRepositoryByIdFetcherOptionalEmpty() {
        Response response = given()
                .queryParam("id", 0)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryByIdFetcherOptional");
        Assertions.assertEquals(response.body().print(), "");
    }

    @Test
    void testBookRepositoryViewById() {
        Response response = given()
                .queryParam("id", 1L)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryViewById");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals(1, response.jsonPath().getLong("id"));
        Assertions.assertNotNull(response.jsonPath().getJsonObject("store"));
        Assertions.assertNotNull(response.jsonPath().getJsonObject("authors"));
    }

    @Test
    void testBookRepositoryFindAllById() {
        String body;
        try {
            body = objectMapper.writeValueAsString(Arrays.asList(1, 2));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllById");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals(1, response.jsonPath().getLong("[0].id"));
    }

    @Test
    void testBookRepositoryFindByIdsFetcher() {
        String body;
        try {
            body = objectMapper.writeValueAsString(Arrays.asList(1, 2));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindByIdsFetcher");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals(1, response.jsonPath().getLong("[0].id"));
        Assertions.assertEquals(2, response.jsonPath().getLong("[0].authors[0].id"));
    }

    @Test
    void testBookRepositoryFindMapByIds() {
        String body;
        try {
            body = objectMapper.writeValueAsString(Arrays.asList(1, 2));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindMapByIds");
        Assertions.assertNotNull(response.jsonPath().getMap(""));
    }

    @Test
    void testBookRepositoryFindMapByIdsFetcher() {
        String body = """
                [1, 2]
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindMapByIdsFetcher");
        Assertions.assertNotNull(response.jsonPath().getMap(""));
        Assertions.assertNotNull(response.jsonPath().getMap("").get("1"));
    }

    @Test
    void testBookRepositoryFindAll() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryFindAll");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals(1, response.jsonPath().getLong("[0].id"));
    }

    @Test
    void testBookRepositoryFindAllTypedPropScalar() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryFindAllTypedPropScalar");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("[0].name"));
    }

    @Test
    void testBookRepositoryFindAllFetcherTypedPropScalar() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryFindAllFetcherTypedPropScalar");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("[0].name"));
        Assertions.assertNotNull(response.jsonPath().getString("[0].authors"));
        Assertions.assertNotNull(response.jsonPath().getString("[0].store"));
    }

    @Test
    void testBookRepositoryFindAllSort() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryFindAllSort");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("[0].name"));
    }

    @Test
    void testBookRepositoryFindAllFetcherSort() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryFindAllFetcherSort");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("[0].name"));
        Assertions.assertNotNull(response.jsonPath().getString("[0].authors"));
        Assertions.assertNotNull(response.jsonPath().getString("[0].store"));
    }

    @Test
    void testBookRepositoryFindAllPageFetcher() {
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllPageFetcher");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertNotNull(response.jsonPath().getString("rows[0].authors"));
        Assertions.assertEquals(6, response.jsonPath().getInt("totalRowCount"));
        Assertions.assertNotNull(response.jsonPath().getString("totalPageCount"));
    }

    @Test
    void testBookRepositoryFindAllPageTypedPropScalar() {
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllPageTypedPropScalar");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertNull(response.jsonPath().getString("rows[0].authors"));
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("rows[0].name"));
        Assertions.assertEquals(6, response.jsonPath().getInt("totalRowCount"));
        Assertions.assertNotNull(response.jsonPath().getString("totalPageCount"));
    }

    @Test
    void testBookRepositoryFindAllPageFetcherTypedPropScalar() {
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllPageFetcherTypedPropScalar");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertNotNull(response.jsonPath().getString("rows[0].authors"));
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("rows[0].name"));
        Assertions.assertEquals(6, response.jsonPath().getInt("totalRowCount"));
        Assertions.assertNotNull(response.jsonPath().getString("totalPageCount"));
    }

    @Test
    void testBookRepositoryFindAllPageSort() {
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllPageSort");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("rows[0].name"));
        Assertions.assertEquals(6, response.jsonPath().getInt("totalRowCount"));
        Assertions.assertNotNull(response.jsonPath().getString("totalPageCount"));
    }

    @Test
    void testBookRepositoryFindAllPageFetcherSort() {
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllPageFetcherSort");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("rows[0].name"));
        Assertions.assertNotNull(response.jsonPath().getString("rows[0].authors"));
        Assertions.assertEquals(6, response.jsonPath().getInt("totalRowCount"));
        Assertions.assertNotNull(response.jsonPath().getString("totalPageCount"));
    }

    @Test
    void testBookRepositoryExistsById() {
        Response response = given()
                .queryParam("id", 0)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryExistsById");
        Assertions.assertFalse(response.jsonPath().getBoolean(""));
    }

    @Test
    void testBookRepositoryCount() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryCount");
        Assertions.assertEquals(6, response.jsonPath().getInt(""));
    }

    @Test
    void testUserRoleRepositoryInsert() {
        String body = """
                {
                     "id": "029253C4-35D3-F78B-5A21-E12D7F358A0B",
                     "userId": "12",
                     "roleId": "213",
                     "deleteFlag": false
                 }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositoryInsert");
        System.out.println("response.body().prettyPrint() = " + response.body().prettyPrint());
        Assertions.assertEquals("029253c4-35d3-f78b-5a21-e12d7f358a0b", response.jsonPath().getString("id"));
        Assertions.assertEquals("12", response.jsonPath().getString("userId"));
        Assertions.assertEquals("213", response.jsonPath().getString("roleId"));
        Assertions.assertFalse(response.jsonPath().getBoolean("deleteFlag"));
    }

    @Test
    void testUserRoleRepositoryInsertInput() {
        String body = """
                {
                     "id": "81D8F7AB-C3FB-A8B6-3B22-C20A26C83B72",
                     "userId": "12",
                     "roleId": "213",
                     "deleteFlag": false
                 }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositoryInsertInput");
        Assertions.assertEquals("81d8f7ab-c3fb-a8b6-3b22-c20a26c83b72", response.jsonPath().getString("id"));
        Assertions.assertEquals("12", response.jsonPath().getString("userId"));
        Assertions.assertEquals("213", response.jsonPath().getString("roleId"));
        Assertions.assertFalse(response.jsonPath().getBoolean("deleteFlag"));
    }

    @Test
    void testUserRoleRepositorySave() {
        String body = """
                {
                     "id": "0C844055-A86E-94D9-2C50-77CAFBBC20AB",
                     "userId": "12",
                     "roleId": "213",
                     "deleteFlag": false
                 }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositorySave");
        Assertions.assertEquals("0c844055-a86e-94d9-2c50-77cafbbc20ab", response.jsonPath().getString("id"));
        Assertions.assertEquals("12", response.jsonPath().getString("userId"));
        Assertions.assertEquals("213", response.jsonPath().getString("roleId"));
        Assertions.assertFalse(response.jsonPath().getBoolean("deleteFlag"));
    }

    @Test
    void testUserRoleRepositorySaveInput() {
        String body = """
                {
                     "id": "EEB27AAA-8FEA-4177-0179-183FCB154B36",
                     "userId": "12",
                     "roleId": "213",
                     "deleteFlag": false
                 }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositorySaveInput");
        Assertions.assertEquals("eeb27aaa-8fea-4177-0179-183fcb154b36", response.jsonPath().getString("id"));
        Assertions.assertEquals("12", response.jsonPath().getString("userId"));
        Assertions.assertEquals("213", response.jsonPath().getString("roleId"));
        Assertions.assertFalse(response.jsonPath().getBoolean("deleteFlag"));
    }

    @Test
    void testUserRoleRepositorySaveInputSaveMode() {
        String body = """
                {
                     "id": "E85FC166-66DD-F496-F733-22BA38DC807D",
                     "userId": "12",
                     "roleId": "213",
                     "deleteFlag": false
                 }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositorySaveInputSaveMode");
        Assertions.assertEquals(1, response.jsonPath().getInt("totalAffectedRowCount"));
        Assertions.assertEquals("e85fc166-66dd-f496-f733-22ba38dc807d", response.jsonPath().getString("originalEntity.id"));
        Assertions.assertEquals("e85fc166-66dd-f496-f733-22ba38dc807d", response.jsonPath().getString("modifiedEntity.id"));
        Assertions.assertTrue(response.jsonPath().getBoolean("modified"));
    }

    @Test
    void testUserRoleRepositorySaveCommand() {
        String body = """
                {
                     "id": "83282150-7E51-D3A9-0EB6-CB606A56873B",
                     "userId": "12",
                     "roleId": "213",
                     "deleteFlag": false
                 }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositorySaveCommand");
        Assertions.assertEquals(response.statusCode(), HttpStatus.SC_OK);
    }

    @Test
    void testUserRoleRepositorySaveEntities() {
        String body = """
                [
                      {
                          "id": "D45493FF-5770-C90D-CFFF-DA11A8C07264",
                          "userId": "12",
                          "roleId": "213",
                          "deleteFlag": false
                      },
                      {
                          "id": "AC3CEADF-151E-BD7E-2D73-D50E5F86B31D",
                          "userId": "333",
                          "roleId": "333",
                          "deleteFlag": false
                      }
                  ]
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositorySaveEntities");
        Assertions.assertEquals(response.statusCode(), HttpStatus.SC_OK);
        Assertions.assertEquals("d45493ff-5770-c90d-cfff-da11a8c07264", response.jsonPath().getString("[0].id"));
        Assertions.assertEquals("ac3ceadf-151e-bd7e-2d73-d50e5f86b31d", response.jsonPath().getString("[1].id"));
    }

    @Test
    void testUserRoleRepositorySaveEntitiesSaveMode() {
        String body = """
                [
                      {
                          "id": "4C1710D4-46E6-33D3-DC53-8492F6664050",
                          "userId": "12",
                          "roleId": "213",
                          "deleteFlag": false
                      },
                      {
                          "id": "10C40E99-B6EB-A6AE-B1C5-61FA87FEF236",
                          "userId": "333",
                          "roleId": "333",
                          "deleteFlag": false
                      }
                  ]
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositorySaveEntitiesSaveMode");
        Assertions.assertEquals(response.statusCode(), HttpStatus.SC_OK);
        Assertions.assertEquals(4, response.jsonPath().getInt("totalAffectedRowCount"));
        Assertions.assertEquals(2, response.jsonPath().getInt("simpleResults[0].totalAffectedRowCount"));
    }

    @Test
    void testUserRoleRepositorySaveEntitiesCommand() {
        String body = """
                [
                      {
                          "id": "8BAD0B39-3BEC-00F8-BE42-7F0D9671A28C",
                          "userId": "12",
                          "roleId": "213",
                          "deleteFlag": false
                      },
                      {
                          "id": "F6EADECF-EE6D-FFC6-F0FA-E7DC353EE735",
                          "userId": "333",
                          "roleId": "333",
                          "deleteFlag": false
                      }
                  ]
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositorySaveEntitiesCommand");
        Assertions.assertEquals(response.statusCode(), HttpStatus.SC_OK);
    }

    @Test
    void testUserRoleRepositoryUpdate() {
        String body = """
                {
                     "id": "defc2d01-fb38-4d31-b006-fd182b25aa33",
                     "userId": "12",
                     "roleId": "213",
                     "deleteFlag": false
                 }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testUserRoleRepositoryUpdate");
        Assertions.assertEquals("defc2d01-fb38-4d31-b006-fd182b25aa33", response.jsonPath().getString("id"));
        Assertions.assertEquals("12", response.jsonPath().getString("userId"));
        Assertions.assertEquals("213", response.jsonPath().getString("roleId"));
        Assertions.assertFalse(response.jsonPath().getBoolean("deleteFlag"));
    }

    @Test
    void testUserRoleRepositoryById() {
        Response response = given()
                .queryParam("id", UUID.fromString("defc2d01-fb38-4d31-b006-fd182b25aa33"))
                .log()
                .all()
                .when()
                .get("testResources/testUserRoleRepositoryById");
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("defc2d01-fb38-4d31-b006-fd182b25aa33", response.jsonPath().getString("id"));
    }

    @Test
    void testUserRoleRepositoryUpdateInput() {
        String body = """
                {
                    "id": "defc2d01-fb38-4d31-b006-fd182b25aa33",
                    "userId": "3",
                    "roleId": "4"
                }
                """;
        Response response = given()
                .body(body)
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .log()
                .all()
                .when()
                .put("testResources/testUserRoleRepositoryUpdateInput");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
    }

    @Test
    void testBookRepositoryFindByIdsView() {
        String body = """
                [1,3,5,7]
                """;
        Response response = given()
                .body(body)
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindByIdsView");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
        Assertions.assertNotNull(response.jsonPath().get("[0].store"));
        Assertions.assertNotNull(response.jsonPath().get("[0].authors"));
    }

    @Test
    void testBookRepositoryFindAllView() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryFindAllView");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
        Assertions.assertNotNull(response.jsonPath().get("[0].store"));
        Assertions.assertNotNull(response.jsonPath().get("[0].authors"));
    }

    @Test
    void testBookRepositoryFindAllTypedPropScalarView() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryFindAllTypedPropScalarView");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
        Assertions.assertEquals(7, response.jsonPath().getLong("[0].id"));
        Assertions.assertNotNull(response.jsonPath().get("[0].store"));
        Assertions.assertNotNull(response.jsonPath().get("[0].authors"));
    }

    @Test
    void testBookRepositoryFindAllSortView() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryFindAllSortView");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
        Assertions.assertEquals(7, response.jsonPath().getLong("[0].id"));
        Assertions.assertNotNull(response.jsonPath().get("[0].store"));
        Assertions.assertNotNull(response.jsonPath().get("[0].authors"));
    }

    @Test
    void testBookRepositoryFindAllPageView() {
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllPageView");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Learning GraphQL", response.jsonPath().getString("rows[0].name"));
        Assertions.assertNotNull(response.jsonPath().getString("rows[0].authors"));
        Assertions.assertEquals(6, response.jsonPath().getInt("totalRowCount"));
        Assertions.assertNotNull(response.jsonPath().getString("totalPageCount"));
    }

    @Test
    void testBookRepositoryFindAllPageTypedPropScalarView() {
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllPageTypedPropScalarView");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("rows[0].name"));
        Assertions.assertNotNull(response.jsonPath().getString("rows[0].authors"));
        Assertions.assertEquals(6, response.jsonPath().getInt("totalRowCount"));
        Assertions.assertNotNull(response.jsonPath().getString("totalPageCount"));
    }

    @Test
    void testBookRepositoryFindAllPageSortView() {
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindAllPageSortView");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("rows[0].name"));
        Assertions.assertNotNull(response.jsonPath().getString("rows[0].authors"));
        Assertions.assertEquals(6, response.jsonPath().getInt("totalRowCount"));
        Assertions.assertNotNull(response.jsonPath().getString("totalPageCount"));
    }

    @Test
    void testBookRepositoryCustomQuery() {
        Response response = given()
                .queryParam("id", 1L)
                .log()
                .all()
                .when()
                .get("testResources/testBookRepositoryCustomQuery");
        Assertions.assertNotNull(response.jsonPath());
    }

    @Test
    void testBookRepositoryFindMapByIdsView() {
        String body = """
                [1, 2]
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryFindMapByIdsView");
        Assertions.assertNotNull(response.jsonPath().getMap(""));
        Assertions.assertNotNull(response.jsonPath().get("1"));
    }

    @Test
    void testBookRepositoryMerge() {
        String body = """
                {
                    "id": 22,
                    "name": "merge",
                    "edition": 1,
                    "price": "10.00",
                    "tenant": "c",
                    "store": {
                        "id": 6,
                        "name": "mergeStore",
                        "website": "mergeWebsite"
                    },
                    "authors": [
                        {
                            "id": 10,
                            "firstName": "merge",
                            "lastName": "merge",
                            "gender": "FEMALE"
                        }
                    ]
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryMerge");
        Assertions.assertEquals(4, response.jsonPath().getInt("totalAffectedRowCount"));
    }

    @Test
    void testBookRepositoryMergeInput() {
        String body = """
                {
                    "id": 55,
                    "name": "mergeInput",
                    "edition": 1,
                    "price": "10.00",
                    "tenant": "d",
                    "storeId": 1,
                    "authors": [
                        {
                            "id": 11,
                            "firstName": "mergeInput",
                            "lastName": "mergeInput",
                            "gender": "FEMALE"
                        }
                    ]
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryMergeInput");
        Assertions.assertEquals(3, response.jsonPath().getInt("totalAffectedRowCount"));
    }

    @Test
    void testBookRepositoryMergeSaveMode() {
        String body = """
                {
                    "id": 77,
                    "name": "mergeSaveMode",
                    "edition": 1,
                    "price": "10.00",
                    "tenant": "c",
                    "store": {
                        "id": 10,
                        "name": "mergeSaveMode",
                        "website": "mergeSaveMode"
                    },
                    "authors": [
                        {
                            "id": 20,
                            "firstName": "mergeSaveMode",
                            "lastName": "mergeSaveMode",
                            "gender": "FEMALE"
                        }
                    ]
                }
                """;
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .body(body)
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryMergeSaveMode");
        Assertions.assertEquals(4, response.jsonPath().getInt("totalAffectedRowCount"));
    }
}
