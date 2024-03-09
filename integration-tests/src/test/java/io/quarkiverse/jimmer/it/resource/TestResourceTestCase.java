package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;

import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkiverse.jimmer.it.repository.UserRoleRepository;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.vertx.core.http.HttpHeaders;

@QuarkusTest
public class TestResourceTestCase {

    @Inject
    BookRepository bookRepository;

    @Inject
    @DataSource("DB2")
    UserRoleRepository userRoleRepository;

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
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
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
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
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
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
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
        String body = """
                {
                    "index": 0,
                    "size": 1
                }
                """;
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
        String body = """
                [1, 2]
                """;
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
        String body = """
                [1, 2]
                """;
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
        String body = """
                [1, 2]
                """;
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
        System.out.println("response.body().prettyPrint() = " + response.body().prettyPrint());
        Assertions.assertNotNull(response.jsonPath());
        Assertions.assertEquals("Programming TypeScript", response.jsonPath().getString("[0].name"));
        Assertions.assertNotNull(response.jsonPath().getString("[0].authors"));
        Assertions.assertNotNull(response.jsonPath().getString("[0].store"));
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
    void testUserRoleRepositoryUpdate() {
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
                .put("testResources/testUserRoleRepositoryUpdate");
        Assertions.assertEquals(response.getStatusCode(), HttpStatus.SC_OK);
    }
}
