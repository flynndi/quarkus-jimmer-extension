package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
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

    @Test
    void testRepository() {
        BookRepository bookRepository = Arc.container().instance(BookRepository.class).get();
        BookStoreRepository bookStoreRepository = Arc.container().instance(BookStoreRepository.class).get();
        Assertions.assertNotNull(bookRepository);
        Assertions.assertNotNull(bookStoreRepository);
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
        Assertions.assertEquals(1, response.jsonPath().getLong("id"));
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
}