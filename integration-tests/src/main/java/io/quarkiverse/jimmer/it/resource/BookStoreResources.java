package io.quarkiverse.jimmer.it.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkiverse.jimmer.it.service.IBookStore;

@Path("/bookStoreResource")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookStoreResources {

    private final IBookStore iBookStore;

    private final BookStoreRepository bookStoreRepository;

    public BookStoreResources(IBookStore iBookStore, BookStoreRepository bookStoreRepository) {
        this.iBookStore = iBookStore;
        this.bookStoreRepository = bookStoreRepository;
    }

    @GET
    @Path("test1")
    public Response test1() {
        return Response.ok(iBookStore.oneToMany()).build();
    }

    @GET
    @Path("testNewestBooks")
    public Response testNewestBooks() {
        return Response.ok(bookStoreRepository.findAll(
                Fetchers.BOOK_STORE_FETCHER
                        .name()
                        .newestBooks(
                                Fetchers.BOOK_FETCHER
                                        .allScalarFields()
                                        .authors(Fetchers.AUTHOR_FETCHER.allScalarFields()))))
                .build();
    }
}
