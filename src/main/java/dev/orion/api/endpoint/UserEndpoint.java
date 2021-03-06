package dev.orion.api.endpoint;

import dev.orion.api.endpoint.body.ConnectUserResponseBody;
import dev.orion.services.interfaces.UserService;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponseSchema;
import org.jboss.resteasy.annotations.jaxrs.PathParam;

import javax.inject.Inject;
import javax.ws.rs.PATCH;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v1/users")
public class UserEndpoint {

    @Inject
    UserService userService;

    @Path("/{externalId}/connect")
    @PATCH
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponseSchema(ConnectUserResponseBody.class)
    public Response connectUser(@Parameter(description = "Id of user to connect", example = "372bf2a5-0da3-47bd-8c94-4a09d25d362a") @PathParam String externalId) {
        userService.connectUser(externalId);
        return Response
                .status(Response.Status.OK)
                .entity(new ConnectUserResponseBody(externalId))
                .build();
    }


}
