package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.IntegrationTestsProfile;
import io.quarkiverse.jimmer.it.repository.TreeNodeRepository;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;

@QuarkusTest
@TestProfile(IntegrationTestsProfile.class)
public class TreeNodeResourcesTestCase {

    @Inject
    TreeNodeRepository treeNodeRepository;

    @Test
    public void testTreeNodeRepository() {
        TreeNodeRepository treeNodeRepository = Arc.container().instance(TreeNodeRepository.class).get();
        Assertions.assertEquals(treeNodeRepository, this.treeNodeRepository);
    }

    @Test
    public void testInfiniteRecursion() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("treeNodeResources/infiniteRecursion");
        Assertions.assertEquals(1, response.jsonPath().getLong("[0].id"));
        Assertions.assertEquals(2, response.jsonPath().getList("[0].childNodes").size());
    }

    @Test
    public void testAll() {
        Response response = given()
                .log()
                .all()
                .when()
                .get("treeNodeResources/all");
        Assertions.assertEquals(24, response.jsonPath().getList("").size());
        Assertions.assertEquals(2, response.jsonPath().getList("[0].childNodes").size());
        Assertions.assertEquals(9, response.jsonPath().getInt("[0].childNodes[0].id"));
        Assertions.assertEquals(2, response.jsonPath().getList("[0].childNodes[0].childNodes").size());
    }
}
