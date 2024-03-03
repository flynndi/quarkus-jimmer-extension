package io.quarkiverse.jimmer.it.resource;

import java.util.ArrayList;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.babyfish.jimmer.client.FetchBy;
import org.babyfish.jimmer.client.meta.Api;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import io.quarkiverse.jimmer.it.config.error.UserInfoException;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkiverse.jimmer.it.service.IBook;

@Path("/bookResource")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api
public class BookResources implements Fetchers {

    private final IBook iBook;

    public BookResources(IBook iBook) {
        this.iBook = iBook;
    }

    @GET
    @Path("/book")
    @Api
    public Response getBookById(@RestQuery int id) {
        return Response.ok(iBook.findById(id)).build();
    }

    @GET
    @Path("/book/{id}")
    @Api
    public @FetchBy("COMPLEX_BOOK") Book getBookByIdFetcher(@RestPath int id) {
        return iBook.findById(id, COMPLEX_BOOK);
    }

    @POST
    @Path("/books")
    @Api
    public Response getBookByIds(List<Integer> ids) {
        return Response.ok(iBook.findByIds(ids)).build();
    }

    @POST
    @Path("/book")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response postBook(Book book) {
        return Response.ok(iBook.save(book)).build();
    }

    @GET
    @Path("/books")
    @Api
    public List<@FetchBy("SIMPLE_BOOK") Book> getBookByNameFetcher(@RestQuery String name) {
        return iBook.findBooksByName(name, SIMPLE_BOOK);
    }

    @GET
    @Path("/booksByName")
    @Api
    public List<Book> getBookByName(@RestQuery String name) {
        return iBook.findBooksByName(name);
    }

    @PUT
    @Path("/update")
    @Api
    public Response update() {
        iBook.update();
        return Response.ok().build();
    }

    @GET
    @Path("/testManyToMany")
    @Api
    public Response testManyToMany() {
        return Response.ok(iBook.manyToMany()).build();
    }

    @PUT
    @Path("/testUpdateOneToMany")
    @Api
    public Response testUpdateOneToMany() {
        iBook.updateOneToMany();
        return Response.ok().build();
    }

    @POST
    @Path("/testSaveManyToMany")
    @Api
    public Response testSaveManyToMany() {
        iBook.saveManyToMany();
        return Response.ok().build();
    }

    @POST
    @Path("/testFile")
    @Api
    public Response testFile(FileUpload filePart) {
        return Response.ok().build();
    }

    @GET
    @Path("/testError")
    @Api
    public Response testError() throws UserInfoException.IllegalUserName {
        List<Character> illegalChars = new ArrayList<>();
        illegalChars.add('a');
        throw UserInfoException.illegalUserName("testError", illegalChars);
    }

    private static final Fetcher<Book> SIMPLE_BOOK = BOOK_FETCHER.name();

    private static final Fetcher<Book> COMPLEX_BOOK = BOOK_FETCHER.allScalarFields()
            .store(BOOK_STORE_FETCHER.name())
            .authors(AUTHOR_FETCHER.firstName().lastName());
}
