package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

@QuarkusTest
public class TestErrorCodeTestCase {

    @Test
    void testHelloEndpoint() {
        Response response = given()
                .when().get("/bookResource/testError");
        Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.statusCode());
        Assertions.assertEquals("USER_INFO", response.body().jsonPath().getString("family"));
        Assertions.assertEquals("ILLEGAL_USER_NAME", response.body().jsonPath().getString("code"));
        Assertions.assertEquals("[a]", response.body().jsonPath().getString("illegalChars"));
    }
}
