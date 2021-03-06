package dev.orion.client;

import dev.orion.client.dto.CreateDocumentResponse;
import dev.orion.client.dto.SendEmailRequest;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v1/")
@ApplicationScoped
@RegisterRestClient(configKey = "api.email-service.client")
public interface EmailClient {

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Timeout(1000)
    CreateDocumentResponse sendEmails(SendEmailRequest sendEmailRequest);
}
