package io.github.psehgaft.game;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class GameResourceTest {
    @Test
    void servesPageAndApi() {
        given().when().get("/").then().statusCode(200).body(containsString("Quarkus Snake"));
        given().when().get("/api/game").then().statusCode(200).body("boardSize", is(20));
        given().contentType("application/json").body("{\"player\":\"Test\",\"points\":5}")
                .when().post("/api/game/scores").then().statusCode(201).body("points", is(5));
        given().when().get("/api/game/scores").then().statusCode(200).body("player", hasItem("Test"));
        given().contentType("application/json").body("{\"player\":\"\",\"points\":-1}")
                .when().post("/api/game/scores").then().statusCode(400);
    }
}
