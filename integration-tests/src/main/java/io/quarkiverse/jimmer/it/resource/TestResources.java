package io.quarkiverse.jimmer.it.resource;

import static io.quarkiverse.jimmer.it.entity.Fetchers.*;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
import io.quarkiverse.jimmer.it.entity.dto.UserRoleInput;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkiverse.jimmer.it.repository.UserRoleRepository;
import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.repository.common.Sort;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkus.agroal.DataSource;

@Path("/testResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TestResources {

    @Inject
    BookRepository bookRepository;

    @Inject
    BookStoreRepository bookStoreRepository;

    @Inject
    @DataSource("DB2")
    UserRoleRepository userRoleRepository;

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
    public Response testBookRepositoryPage(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination)).build();
    }

    @POST
    @Path("/testBookRepositoryPageOther")
    public Response testBookRepositoryPageOther(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size)).build();
    }

    @POST
    @Path("/testBookRepositoryPageSort")
    public Response testBookRepositoryPageSort(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, Sort.by(Sort.Direction.DESC, "id")))
                .build();
    }

    @POST
    @Path("/testBookRepositoryPageFetcher")
    public org.babyfish.jimmer.Page<@FetchBy("COMPLEX_BOOK") Book> testBookRepositoryPageFetcher(Pagination pagination) {
        return bookRepository.findAll(pagination, COMPLEX_BOOK);
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

    @GET
    @Path("/testUserRoleRepositoryById")
    public Response UserRoleRepositoryById(@RestQuery UUID id) {
        return Response.ok(userRoleRepository.findNullable(id)).build();
    }

    @PUT
    @Path("/testUserRoleRepositoryUpdate")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositoryTransactional(UserRoleInput userRoleInput) {
        userRoleRepository.update(userRoleInput);
        return Response.ok().build();
    }

    private static final Fetcher<Book> COMPLEX_BOOK = BOOK_FETCHER.allScalarFields()
            .store(BOOK_STORE_FETCHER.name())
            .authors(AUTHOR_FETCHER.firstName().lastName());
}
