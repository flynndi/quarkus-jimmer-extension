package io.quarkiverse.jimmer.runtime.cloud;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient
@Path("/jimmerMicroServiceBridge")
public interface QuarkusExchangeRestClient {

    @GET
    @Path("/byIds")
    @Produces(MediaType.APPLICATION_JSON)
    String findByIds(@QueryParam("ids") String ids, @QueryParam("fetcher") String fetcher);

    @GET
    @Path("/byAssociatedIds")
    @Produces(MediaType.APPLICATION_JSON)
    String findByAssociatedIds(@QueryParam("prop") String prop,
            @QueryParam("targetIds") String targetIdArrStr,
            @QueryParam("fetcher") String fetcherStr);
}
