package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

@QuarkusIntegrationTest
@TestProfile(NamedDataSourceColdStartIT.ColdStartProfile.class)
class NamedDataSourceColdStartIT {

    // A distinct profile makes Quarkus launch a fresh application even when other HTTP tests run first.
    public static class ColdStartProfile implements QuarkusTestProfile {
    }

    @Test
    void firstRequestCommitsToTheNamedDataSource() {
        String id = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String roleId = UUID.randomUUID().toString();

        given().contentType(ContentType.JSON).body(Map.of("id", id, "userId", userId, "roleId", roleId))
                .post("/userRoleResources")
                .then().statusCode(200).body("id", equalTo(id));

        given().queryParam("userId", userId).queryParam("roleId", roleId)
                .get("/userRoleResources/testUserRoleSpecification")
                .then().statusCode(200).body("id", contains(id));
    }
}
