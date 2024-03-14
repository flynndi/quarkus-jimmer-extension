package io.quarkiverse.jimmer.runtime.cloud;

import java.util.Collection;
import java.util.List;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient
public interface QuarkusExchangeRestClient {

    @GET
    @Path("/jimmerMicroServiceBridge/byIds")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    String findByIds(@QueryParam("ids") Collection<?> ids, @QueryParam("fetcher") String fetcher);
}
