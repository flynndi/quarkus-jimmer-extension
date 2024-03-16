package io.quarkiverse.jimmer.runtime.cloud;

import java.util.Collection;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient
public interface QuarkusExchangeRestClient {

    @GET
    @Path("/jimmerMicroServiceBridge/byIds")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    String findByIds(@QueryParam("ids") String ids, @QueryParam("fetcher") String fetcher);
}
