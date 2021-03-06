package dev.orion.api.endpoint;

import dev.orion.api.endpoint.body.*;
import dev.orion.entity.Activity;
import dev.orion.services.interfaces.ActivityService;
import lombok.val;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.annotations.jaxrs.PathParam;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/v1/activities")
public class ActivityEndpoint {

    @Inject
    ActivityService activityService;

    @GET
    @Path("/{activityUuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponseSchema(Activity.class)
    public Response findActivity(@Parameter(description = "UUID of activity to be started", example = "372bf2a5-0da3-47bd-8c94-4a09d25d362a") @PathParam String activityUuid) {
        Activity activity = (Activity) Activity
                .findByIdOptional(UUID.fromString(activityUuid))
                .orElseThrow(() -> new NotFoundException(
                        MessageFormat.format("Activity {0} not found", activityUuid)));

        return Response
                .ok(activity)
                .build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponses({
            @APIResponse(
                    content = {@Content(schema = @Schema(implementation = CreateActivityResponseBody.class))},
                    description = "Create a new activity",
                    responseCode = "201"
            ),
            @APIResponse(
                    content = {@Content(schema = @Schema(implementation = DefaultErrorResponseBody.class))},
                    description = "List of errors of trying add a invalid user",
                    responseCode = "400"
            )
    })
    public Response createActivity(@Valid CreateActivityRequestBody createActivityRequestBody) {
        val activityUuid = activityService.createActivity(createActivityRequestBody.getUserExternalId(), createActivityRequestBody.getWorkflowName());
        val activity = (Activity) Activity.findById(activityUuid);

        val responseBody = new CreateActivityResponseBody();
        responseBody.setUuid(activityUuid);
        responseBody.setGroups(activity.getGroupActivities().stream().map(groupActivity -> groupActivity.getUuid()).collect(Collectors.toList()));

        return Response
                .status(Response.Status.CREATED)
                .entity(responseBody)
                .build();
    }

    @POST
    @Path("/{activityUuid}/addUser")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponses({
            @APIResponse(
                    content = {@Content(schema = @Schema(implementation = AddUserToActivityResponseBody.class))},
                    description = "Add user into activity to participate.",
                    responseCode = "200"
            ),
            @APIResponse(
                    content = {@Content(schema = @Schema(implementation = DefaultErrorResponseBody.class))},
                    description = "List of errors of trying add a invalid user",
                    responseCode = "400"
            )
    })

    public Response addUserToActivity(
            @Valid AddUserToActivityRequestBody addUserToActivityRequestBody,
            @Parameter(description = "UUID of activity to be started", example = "372bf2a5-0da3-47bd-8c94-4a09d25d362a") @PathParam String activityUuid) {
        var activity = activityService.addUserInActivity(UUID.fromString(activityUuid), addUserToActivityRequestBody.userExternalId);
        var responseBody = new AddUserToActivityResponseBody(activity);

        return Response
                .status(Response.Status.OK)
                .entity(responseBody)
                .build();
    }

    @PATCH
    @Path("/{activityUuid}/start")
    @APIResponse(
            responseCode = "200",
            description = "Start a activity",
            content = @Content(schema = @Schema(implementation = StartActivityResponseBody.class))
    )
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response startActivity(@Parameter(description = "UUID of activity to be started", example = "372bf2a5-0da3-47bd-8c94-4a09d25d362a") @PathParam String activityUuid) {
        val activity = activityService.startActivity(UUID.fromString(activityUuid));

        return Response
                .ok(new StartActivityResponseBody(activity))
                .build();
    }

}
