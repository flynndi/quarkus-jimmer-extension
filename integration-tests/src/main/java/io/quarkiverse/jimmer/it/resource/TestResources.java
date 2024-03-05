package io.quarkiverse.jimmer.it.resource;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookFetcher;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.repository.support.Page;

@Path("/testResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TestResources {

    @Inject
    BookRepository bookRepository;

    @Inject
    BookStoreRepository bookStoreRepository;

    @GET
    @Path("/test")
    public Response test() {
        JSqlClient defaultJSqlClient = Jimmer.getDefaultJSqlClient();
        List<Book> books = defaultJSqlClient
                .createQuery(Tables.BOOK_TABLE)
                .select(Tables.BOOK_TABLE.fetch(BookFetcher.$.allTableFields()))
                .execute();
        return Response.ok(books).build();
    }

    @GET
    @Path("/testBookRepository")
    public Response testBookRepository() {
        return Response.ok(bookRepository.findAll()).build();
    }

    @GET
    @Path("/testBookStoreRepository")
    public Response testRepository() {
        return Response.ok(bookStoreRepository.findAll()).build();
    }

    @POST
    @Path("/testBookRepositoryPage")
    public Response testRepository(Page page) {
        return Response.ok(bookRepository.findAll(page)).build();
    }
}
