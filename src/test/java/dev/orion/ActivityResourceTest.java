package dev.orion;

import dev.orion.api.endpoint.v1.dto.CreateActivityRequestDtoV1;
import dev.orion.client.UserClient;
import dev.orion.client.dto.UserClientDto;
import dev.orion.data.entity.Activity;
import dev.orion.data.entity.Document;
import dev.orion.data.entity.User;
import dev.orion.services.interfaces.ActivityService;
import dev.orion.util.enums.UserStatus;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.Mock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import net.bytebuddy.utility.RandomString;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jose4j.jwk.Use;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
class ActivityResourceTest {

    @InjectMock
    @RestClient
    UserClient userClient;

    @Inject
    ActivityService activityService;

    final private String userExternalId = RandomString.make();
    final private String userName = RandomString.make();
    final private String activityEndpoint = "/v1/activity";

    @BeforeEach
    public void setup() {
        UserClientDto userClientDto = new UserClientDto(userExternalId, userName, true);
        Mockito
                .when(userClient
                        .getUserByExternalId(userExternalId))
                .thenReturn(userClientDto);
    }

    @Test
    @DisplayName("It should create an activity when user is OK")
    void testActivityCreationOK() {
        var requestDto = new CreateActivityRequestDtoV1();
        requestDto.setUserId(userExternalId);

        given()
                .contentType("application/json")
                .body(requestDto)
                .when()
                .post(activityEndpoint)
                    .then()
                    .statusCode(Response.Status.CREATED.getStatusCode())
                    .body("uuid", notNullValue());
    }

    @Test
    @DisplayName("It should validate the request body")
    void testActivityCreationValidation() {
        var requestDto = new CreateActivityRequestDtoV1();

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestDto)
                .when()
                .post(activityEndpoint)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body("errors[0]", is("User may not be blank"));
    }

    @Test
    @DisplayName("It should throw a bad request when user is already in an activity")
    @Transactional
    void testActivityCreationUserError(){
        var requestDto = new CreateActivityRequestDtoV1();
        requestDto.setUserId(userExternalId);
        var user = new User();
        user.status = UserStatus.CONNECTED;
        user.externalId = userExternalId;
        PanacheMock.mock(User.class);
        Mockito
                .when(User.findUserByExternalId(userExternalId))
                .thenReturn(Optional.of(user));
        System.out.println("User external ID " + User.findUserByExternalId(userExternalId).get().status);


        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestDto)
                .when()
                .post(activityEndpoint)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body("errors[0]", is(MessageFormat.format("User {0} is not available to join activity", userExternalId)));
    }

    @Test
    @DisplayName("It should throw a bad request when user is deactivated")
    void testActivityCreationUserDeactivate(){
        var requestDto = new CreateActivityRequestDtoV1();
        requestDto.setUserId(userExternalId);
        UserClientDto userClientDto = new UserClientDto(userExternalId, userName, false);
        Mockito
                .when(userClient
                        .getUserByExternalId(userExternalId))
                .thenReturn(userClientDto);

        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestDto)
                .when()
                .post(activityEndpoint)
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode())
                .body("errors[0]", is(MessageFormat.format("User {0} is not available to join activity", userExternalId)));
    }
}
