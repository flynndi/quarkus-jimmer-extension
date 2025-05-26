package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

@QuarkusTest
public class UserRoleResourcesTestCase {

    @Test
    public void testUserRole() {
        Response response = given()
                .queryParam("id", Constant.USER_ROLE_ID)
                .log()
                .all()
                .when()
                .get("userRoleResources/userRoleFindById");
        Assertions.assertEquals(Constant.USER_ROLE_ID, response.jsonPath().getString("id"));
        Assertions.assertFalse(response.jsonPath().getBoolean("deleteFlag"));
    }

    @Test
    public void testUpdateUserRole() {
        Response response = given()
                .queryParam("id", Constant.USER_ROLE_ID)
                .log()
                .all()
                .when()
                .put("userRoleResources/updateUserRoleById");
        Assertions.assertEquals(HttpStatus.SC_OK, response.statusCode());
    }

    @Test
    public void testUserRoleSpecification() {
        Response response = given()
                .queryParam("userId", Constant.USER_ID)
                .log()
                .all()
                .when()
                .get("userRoleResources/testUserRoleSpecification");
        Assertions.assertEquals(HttpStatus.SC_OK, response.statusCode());
    }
}
