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
import org.babyfish.jimmer.sql.ast.mutation.DeleteMode;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.entity.*;
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
    @Path("/testBookRepositoryByIdOptional")
    public Response testBookRepositoryByIdOptional(@RestQuery long id) {
        if (bookRepository.findById(id).isPresent()) {
            return Response.ok(bookRepository.findById(id).get()).build();
        } else {
            return Response.noContent().build();
        }
    }

    @GET
    @Path("/testBookRepositoryByIdFetcher")
    public @FetchBy("COMPLEX_BOOK") Book testBookRepositoryByIdFetcher(@RestQuery long id) {
        return bookRepository.findNullable(id, COMPLEX_BOOK);
    }

    @GET
    @Path("/testBookRepositoryByIdFetcherOptional")
    public @FetchBy("COMPLEX_BOOK") Book testBookRepositoryByIdFetcherOptional(@RestQuery long id) {
        if (bookRepository.findById(id, COMPLEX_BOOK).isPresent()) {
            return bookRepository.findById(id, COMPLEX_BOOK).get();
        } else {
            return null;
        }
    }

    @GET
    @Path("/testBookRepositoryViewById")
    public Response testBookRepositoryViewById(@RestQuery long id) {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findNullable(id)).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllById")
    public Response testBookRepositoryFindAllById(List<Long> ids) {
        return Response.ok(bookRepository.findAllById(ids)).build();
    }

    @POST
    @Path("/testBookRepositoryFindByIdsFetcher")
    public Response testBookRepositoryFindByIdsFetcher(List<Long> ids) {
        return Response.ok(bookRepository.findByIds(ids, COMPLEX_BOOK)).build();
    }

    @POST
    @Path("/testBookRepositoryFindMapByIds")
    public Response testBookRepositoryFindMapByIds(List<Long> ids) {
        return Response.ok(bookRepository.findMapByIds(ids)).build();
    }

    @POST
    @Path("/testBookRepositoryFindMapByIdsFetcher")
    public Response testBookRepositoryFindMapByIdsFetcher(List<Long> ids) {
        return Response.ok(bookRepository.findMapByIds(ids, COMPLEX_BOOK)).build();
    }

    @GET
    @Path("/testBookRepositoryFindAll")
    public Response testBookRepositoryFindAll() {
        return Response.ok(bookRepository.findAll()).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllTypedPropScalar")
    public Response testBookRepositoryFindAllTypedPropScalar() {
        return Response.ok(bookRepository.findAll(BookProps.NAME.desc())).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllFetcherTypedPropScalar")
    public Response testBookRepositoryFindAllFetcherTypedPropScalar() {
        return Response.ok(bookRepository.findAll(COMPLEX_BOOK, BookProps.NAME.desc())).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllSort")
    public Response testBookRepositoryFindAllSort() {
        return Response.ok(bookRepository.findAll(Sort.by(Sort.Order.desc("name")))).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllFetcherSort")
    public Response testBookRepositoryFindAllFetcherSort() {
        return Response.ok(bookRepository.findAll(COMPLEX_BOOK, Sort.by(Sort.Order.desc("name")))).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageFetcher")
    public Response testBookRepositoryFindAllPageFetcher(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, COMPLEX_BOOK)).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageTypedPropScalar")
    public Response testBookRepositoryFindAllPageTypedPropScalar(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, BookProps.NAME.desc())).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageFetcherTypedPropScalar")
    public Response testBookRepositoryFindAllPageFetcherTypedPropScalar(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, COMPLEX_BOOK, BookProps.NAME.desc()))
                .build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageSort")
    public Response testBookRepositoryFindAllPageSort(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, Sort.by(Sort.Order.desc("name")))).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageFetcherSort")
    public Response testBookRepositoryFindAllPageFetcherSort(Pagination pagination) {
        return Response
                .ok(bookRepository.findAll(pagination.index, pagination.size, COMPLEX_BOOK, Sort.by(Sort.Order.desc("name"))))
                .build();
    }

    @GET
    @Path("/testBookRepositoryExistsById")
    public Response testBookRepositoryExistsById(@RestQuery long id) {
        return Response.ok(bookRepository.existsById(id)).build();
    }

    @GET
    @Path("/testBookRepositoryCount")
    public Response testBookRepositoryCount(@RestQuery long id) {
        return Response.ok(bookRepository.count()).build();
    }

    @POST
    @Path("/testUserRoleRepositoryInsert")
    public Response testUserRoleRepositoryInsert(UserRole userRole) {
        return Response.ok(userRoleRepository.insert(userRole)).build();
    }

    @POST
    @Path("/testUserRoleRepositoryInsertInput")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositoryInsertInput(UserRoleInput userRoleInput) {
        return Response.ok(userRoleRepository.insert(userRoleInput)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySave")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositorySave(UserRole userRole) {
        return Response.ok(userRoleRepository.save(userRole)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveInput")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositorySaveInput(UserRoleInput userRoleInput) {
        return Response.ok(userRoleRepository.save(userRoleInput)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveInputSaveMode")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositorySaveInputSaveMode(UserRoleInput userRoleInput) {
        return Response.ok(userRoleRepository.save(userRoleInput, SaveMode.INSERT_ONLY)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveCommand")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositorySaveCommand(UserRoleInput userRoleInput) {
        return Response.ok(userRoleRepository.saveCommand(userRoleInput)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveEntities")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositorySaveEntities(List<UserRole> list) {
        return Response.ok(userRoleRepository.saveEntities(list)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveEntitiesSaveMode")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositorySaveEntitiesSaveMode(List<UserRole> list) {
        return Response.ok(userRoleRepository.saveEntities(list, SaveMode.INSERT_ONLY)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveEntitiesCommand")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositorySaveEntitiesCommand(List<UserRole> list) {
        return Response.ok(userRoleRepository.saveEntitiesCommand(list)).build();
    }

    @DELETE
    @Path("/testUserRoleRepositoryDeleteAll")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositoryDeleteAll(List<UserRole> list) {
        return Response.ok(userRoleRepository.deleteAll(list, DeleteMode.AUTO)).build();
    }

    @POST
    @Path("/testUserRoleRepositoryUpdate")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositoryUpdate(UserRole userRole) {
        return Response.ok(userRoleRepository.update(userRole)).build();
    }

    @GET
    @Path("/testUserRoleRepositoryById")
    public Response UserRoleRepositoryById(@RestQuery UUID id) {
        return Response.ok(userRoleRepository.findNullable(id)).build();
    }

    @PUT
    @Path("/testUserRoleRepositoryUpdateInput")
    @Transactional(rollbackOn = Exception.class)
    public Response testUserRoleRepositoryUpdateInput(UserRoleInput userRoleInput) {
        userRoleRepository.update(userRoleInput);
        return Response.ok().build();
    }

    @POST
    @Path("/testBookRepositoryFindByIdsView")
    public Response testBookRepositoryFindByIdsView(List<Long> ids) {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findByIds(ids)).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllView")
    public Response testBookRepositoryFindAllView() {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findAll()).build();
    }

    private static final Fetcher<Book> COMPLEX_BOOK = BOOK_FETCHER.allScalarFields()
            .store(BOOK_STORE_FETCHER.name())
            .authors(AUTHOR_FETCHER.firstName().lastName());
}
