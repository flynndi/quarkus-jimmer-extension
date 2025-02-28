package io.quarkiverse.jimmer.it.resource;

import static io.restassured.RestAssured.given;

import jakarta.inject.Inject;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.quarkiverse.jimmer.it.event.TestChangeEventObserves;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.http.HttpHeaders;

@QuarkusTest
public class TestEvent {

    @Inject
    TestChangeEventObserves testChangeEventObserves;

    @BeforeEach
    void clearEvents() {
        testChangeEventObserves.getEntityEventStorage().clear();
        testChangeEventObserves.getAssociationEventStorageOne().clear();
        testChangeEventObserves.getAssociationEventStorageTwo().clear();
    }

    @Test
    void testEvent() {
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
        Assertions.assertFalse(testChangeEventObserves.getEntityEventStorage().isEmpty());
        Assertions.assertFalse(testChangeEventObserves.getAssociationEventStorageOne().isEmpty());
        Assertions.assertFalse(testChangeEventObserves.getAssociationEventStorageTwo().isEmpty());
        Assertions.assertEquals(1, testChangeEventObserves.getEntityEventStorage().size());
        Assertions.assertEquals(1, testChangeEventObserves.getAssociationEventStorageOne().size());
        Assertions.assertEquals(1, testChangeEventObserves.getAssociationEventStorageTwo().size());
    }

    @Test
    void testEvent2() {
        Response response = given()
                .header(new Header(HttpHeaders.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_JSON.toString()))
                .log()
                .all()
                .when()
                .post("testResources/testEvent");
        Assertions.assertEquals(HttpStatus.SC_OK, response.getStatusCode());
        Assertions.assertEquals(1, testChangeEventObserves.getEntityEventStorage().size());
        Assertions.assertEquals(1, testChangeEventObserves.getAssociationEventStorageOne().size());
        Assertions.assertEquals(2, testChangeEventObserves.getAssociationEventStorageTwo().size());
    }
}
