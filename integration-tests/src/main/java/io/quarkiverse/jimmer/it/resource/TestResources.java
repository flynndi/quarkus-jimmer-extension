package io.quarkiverse.jimmer.it.resource;

import static io.quarkiverse.jimmer.it.entity.Fetchers.*;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.babyfish.jimmer.client.FetchBy;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookFetcher;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.it.entity.dto.BookDetailView;
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
    public Response testBookRepositoryPage(Page page) {
        return Response.ok(bookRepository.findAll(page)).build();
    }

    @POST
    @Path("/testBookRepositoryPageOther")
    public Response testBookRepositoryPageOther(Page page) {
        return Response.ok(bookRepository.findAll(page.index, page.size)).build();
    }

    @POST
    @Path("/testBookRepositoryPageFetcher")
    public org.babyfish.jimmer.Page<@FetchBy("COMPLEX_BOOK") Book> testBookRepositoryPageFetcher(Page page) {
        return bookRepository.findAll(page, COMPLEX_BOOK);
    }

    @GET
    @Path("/testBookRepositoryById")
    public Response testBookRepositoryById(@RestQuery long id) {
        return Response.ok(bookRepository.findNullable(id)).build();
    }

    @GET
    @Path("/testBookRepositoryByIdFetcher")
    public @FetchBy("COMPLEX_BOOK") Book testBookRepositoryByIdFetcher(@RestQuery long id) {
        return bookRepository.findNullable(id, COMPLEX_BOOK);
    }

    @GET
    @Path("/testBookRepositoryViewById")
    public Response testBookRepositoryViewById(@RestQuery long id) {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findNullable(id)).build();
    }

    private static final Fetcher<Book> COMPLEX_BOOK = BOOK_FETCHER.allScalarFields()
            .store(BOOK_STORE_FETCHER.name())
            .authors(AUTHOR_FETCHER.firstName().lastName());
}
