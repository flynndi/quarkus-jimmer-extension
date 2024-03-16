package io.quarkiverse.jimmer.runtime.cloud;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient
public interface QuarkusExchangeRestClient {

    @GET
    @Path("/url")
    String findByIds(@QueryParam("ids") String ids, @QueryParam("fetcher") String fetcher);
}
