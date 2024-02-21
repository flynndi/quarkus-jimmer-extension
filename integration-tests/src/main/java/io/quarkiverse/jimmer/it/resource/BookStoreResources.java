package io.quarkiverse.jimmer.it.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.jimmer.it.service.IBookStore;

@Path("/bookStoreResource")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookStoreResources {

    private final IBookStore iBookStore;

    public BookStoreResources(IBookStore iBookStore) {
        this.iBookStore = iBookStore;
    }

    @GET
    @Path("test1")
    public Response test1() {
        return Response.ok(iBookStore.oneToMany()).build();
    }
}
