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
        Assertions.assertEquals(response.statusCode(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
        Assertions.assertEquals(response.body().jsonPath().getString("family"), "USER_INFO");
        Assertions.assertEquals(response.body().jsonPath().getString("code"), "ILLEGAL_USER_NAME");
        Assertions.assertEquals(response.body().jsonPath().getString("illegalChars"), "[a]");
    }
}
