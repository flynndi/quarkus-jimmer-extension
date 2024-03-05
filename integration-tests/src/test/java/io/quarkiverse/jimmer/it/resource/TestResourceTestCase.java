package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.post;

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
        Response post = given()
                .body(body)
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .log()
                .all()
                .when()
                .post("testResources/testBookRepositoryPage");
        JsonPath responseJsonPath = post.jsonPath();
        Assertions.assertEquals(6, responseJsonPath.getInt("totalRowCount"));
        Assertions.assertEquals(6, responseJsonPath.getInt("totalPageCount"));
    }
}
