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
import org.babyfish.jimmer.client.meta.Api;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode;
import org.babyfish.jimmer.sql.ast.mutation.DeleteMode;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.config.Constant;
import io.quarkiverse.jimmer.it.entity.*;
import io.quarkiverse.jimmer.it.entity.dto.BookDetailView;
import io.quarkiverse.jimmer.it.entity.dto.BookInput;
import io.quarkiverse.jimmer.it.entity.dto.UserRoleInput;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkiverse.jimmer.it.repository.UserRoleRepository;
import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.model.SortUtils;
import io.quarkiverse.jimmer.runtime.repository.QuarkusOrders;
import io.quarkiverse.jimmer.runtime.repository.common.Sort;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkus.agroal.DataSource;

@Path("/testResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api("test")
public class TestResources {

    @Inject
    BookRepository bookRepository;

    @Inject
    BookStoreRepository bookStoreRepository;

    @Inject
    @DataSource(Constant.DATASOURCE2)
    UserRoleRepository userRoleRepository;

    @GET
    @Path("/test")
    @Api
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
    @Api
    public Response testBookRepository() {
        return Response.ok(bookRepository.findAll()).build();
    }

    @GET
    @Path("/testBookStoreRepository")
    @Api
    public Response testRepository() {
        return Response.ok(bookStoreRepository.findAll()).build();
    }

    @POST
    @Path("/testBookRepositoryPage")
    @Api
    public Response testBookRepositoryPage(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination)).build();
    }

    @POST
    @Path("/testBookRepositoryPageOther")
    @Api
    public Response testBookRepositoryPageOther(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size)).build();
    }

    @POST
    @Path("/testBookRepositoryPageSort")
    @Api
    public Response testBookRepositoryPageSort(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, Sort.by(Sort.Direction.DESC, "id")))
                .build();
    }

    @POST
    @Path("/testBookRepositoryPageFetcher")
    @Api
    public org.babyfish.jimmer.Page<@FetchBy("COMPLEX_BOOK") Book> testBookRepositoryPageFetcher(Pagination pagination) {
        return bookRepository.findAll(pagination, COMPLEX_BOOK);
    }

    @GET
    @Path("/testBookRepositoryById")
    @Api
    public Response testBookRepositoryById(@RestQuery long id) {
        return Response.ok(bookRepository.findNullable(id)).build();
    }

    @GET
    @Path("/testBookRepositoryByIdOptional")
    @Api
    public Response testBookRepositoryByIdOptional(@RestQuery long id) {
        if (bookRepository.findById(id).isPresent()) {
            return Response.ok(bookRepository.findById(id).get()).build();
        } else {
            return Response.noContent().build();
        }
    }

    @GET
    @Path("/testBookRepositoryByIdFetcher")
    @Api
    public @FetchBy("COMPLEX_BOOK") Book testBookRepositoryByIdFetcher(@RestQuery long id) {
        return bookRepository.findNullable(id, COMPLEX_BOOK);
    }

    @GET
    @Path("/testBookRepositoryByIdFetcherOptional")
    @Api
    public @FetchBy("COMPLEX_BOOK") Book testBookRepositoryByIdFetcherOptional(@RestQuery long id) {
        if (bookRepository.findById(id, COMPLEX_BOOK).isPresent()) {
            return bookRepository.findById(id, COMPLEX_BOOK).get();
        } else {
            return null;
        }
    }

    @GET
    @Path("/testBookRepositoryViewById")
    @Api
    public Response testBookRepositoryViewById(@RestQuery long id) {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findNullable(id)).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllById")
    @Api
    public Response testBookRepositoryFindAllById(List<Long> ids) {
        return Response.ok(bookRepository.findAllById(ids)).build();
    }

    @POST
    @Path("/testBookRepositoryFindByIdsFetcher")
    @Api
    public Response testBookRepositoryFindByIdsFetcher(List<Long> ids) {
        return Response.ok(bookRepository.findByIds(ids, COMPLEX_BOOK)).build();
    }

    @POST
    @Path("/testBookRepositoryFindMapByIds")
    @Api
    public Response testBookRepositoryFindMapByIds(List<Long> ids) {
        return Response.ok(bookRepository.findMapByIds(ids)).build();
    }

    @POST
    @Path("/testBookRepositoryFindMapByIdsFetcher")
    @Api
    public Response testBookRepositoryFindMapByIdsFetcher(List<Long> ids) {
        return Response.ok(bookRepository.findMapByIds(ids, COMPLEX_BOOK)).build();
    }

    @GET
    @Path("/testBookRepositoryFindAll")
    @Api
    public Response testBookRepositoryFindAll() {
        return Response.ok(bookRepository.findAll()).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllTypedPropScalar")
    @Api
    public Response testBookRepositoryFindAllTypedPropScalar() {
        return Response.ok(bookRepository.findAll(BookProps.NAME.desc())).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllFetcherTypedPropScalar")
    @Api
    public Response testBookRepositoryFindAllFetcherTypedPropScalar() {
        return Response.ok(bookRepository.findAll(COMPLEX_BOOK, BookProps.NAME.desc())).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllSort")
    @Api
    public Response testBookRepositoryFindAllSort() {
        return Response.ok(bookRepository.findAll(Sort.by(Sort.Order.desc("name")))).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllFetcherSort")
    @Api
    public Response testBookRepositoryFindAllFetcherSort() {
        return Response.ok(bookRepository.findAll(COMPLEX_BOOK, Sort.by(Sort.Order.desc("name")))).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageFetcher")
    @Api
    public Response testBookRepositoryFindAllPageFetcher(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, COMPLEX_BOOK)).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageTypedPropScalar")
    @Api
    public Response testBookRepositoryFindAllPageTypedPropScalar(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, BookProps.NAME.desc())).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageFetcherTypedPropScalar")
    @Api
    public Response testBookRepositoryFindAllPageFetcherTypedPropScalar(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, COMPLEX_BOOK, BookProps.NAME.desc()))
                .build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageSort")
    @Api
    public Response testBookRepositoryFindAllPageSort(Pagination pagination) {
        return Response.ok(bookRepository.findAll(pagination.index, pagination.size, Sort.by(Sort.Order.desc("name")))).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageFetcherSort")
    @Api
    public Response testBookRepositoryFindAllPageFetcherSort(Pagination pagination) {
        return Response
                .ok(bookRepository.findAll(pagination.index, pagination.size, COMPLEX_BOOK, Sort.by(Sort.Order.desc("name"))))
                .build();
    }

    @GET
    @Path("/testBookRepositoryExistsById")
    @Api
    public Response testBookRepositoryExistsById(@RestQuery long id) {
        return Response.ok(bookRepository.existsById(id)).build();
    }

    @GET
    @Path("/testBookRepositoryCount")
    @Api
    public Response testBookRepositoryCount() {
        return Response.ok(bookRepository.count()).build();
    }

    @POST
    @Path("/testUserRoleRepositoryInsert")
    @Api
    public Response testUserRoleRepositoryInsert(UserRole userRole) {
        return Response.ok(userRoleRepository.insert(userRole)).build();
    }

    @POST
    @Path("/testUserRoleRepositoryInsertInput")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositoryInsertInput(UserRoleInput userRoleInput) {
        return Response.ok(userRoleRepository.insert(userRoleInput)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySave")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositorySave(UserRole userRole) {
        return Response.ok(userRoleRepository.save(userRole)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveInput")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositorySaveInput(UserRoleInput userRoleInput) {
        return Response.ok(userRoleRepository.save(userRoleInput)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveInputSaveMode")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositorySaveInputSaveMode(UserRoleInput userRoleInput) {
        return Response.ok(userRoleRepository.save(userRoleInput, SaveMode.INSERT_ONLY)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveCommand")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositorySaveCommand(UserRoleInput userRoleInput) {
        return Response.ok(userRoleRepository.saveCommand(userRoleInput)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveEntities")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositorySaveEntities(List<UserRole> list) {
        return Response.ok(userRoleRepository.saveEntities(list)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveEntitiesSaveMode")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositorySaveEntitiesSaveMode(List<UserRole> list) {
        return Response.ok(userRoleRepository.saveEntities(list, SaveMode.INSERT_ONLY)).build();
    }

    @POST
    @Path("/testUserRoleRepositorySaveEntitiesCommand")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositorySaveEntitiesCommand(List<UserRole> list) {
        return Response.ok(userRoleRepository.saveEntitiesCommand(list)).build();
    }

    @DELETE
    @Path("/testUserRoleRepositoryDeleteAll")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositoryDeleteAll(List<UserRole> list) {
        return Response.ok(userRoleRepository.deleteAll(list, DeleteMode.AUTO)).build();
    }

    @POST
    @Path("/testUserRoleRepositoryUpdate")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositoryUpdate(UserRole userRole) {
        return Response.ok(userRoleRepository.update(userRole)).build();
    }

    @GET
    @Path("/testUserRoleRepositoryById")
    @Api
    public Response UserRoleRepositoryById(@RestQuery UUID id) {
        return Response.ok(userRoleRepository.findNullable(id)).build();
    }

    @PUT
    @Path("/testUserRoleRepositoryUpdateInput")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response testUserRoleRepositoryUpdateInput(UserRoleInput userRoleInput) {
        userRoleRepository.update(userRoleInput);
        return Response.ok().build();
    }

    @POST
    @Path("/testBookRepositoryFindByIdsView")
    @Api
    public Response testBookRepositoryFindByIdsView(List<Long> ids) {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findByIds(ids)).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllView")
    @Api
    public Response testBookRepositoryFindAllView() {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findAll()).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllTypedPropScalarView")
    @Api
    public Response testBookRepositoryFindAllTypedPropScalarView() {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findAll(BookProps.NAME.desc())).build();
    }

    @GET
    @Path("/testBookRepositoryFindAllSortView")
    @Api
    public Response testBookRepositoryFindAllSortView() {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findAll(Sort.by(Sort.Order.desc("name")))).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageView")
    @Api
    public Response testBookRepositoryFindAllPageView(Pagination pagination) {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findAll(pagination.index, pagination.size)).build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageTypedPropScalarView")
    @Api
    public Response testBookRepositoryFindAllPageTypedPropScalarView(Pagination pagination) {
        return Response.ok(
                bookRepository.viewer(BookDetailView.class).findAll(pagination.index, pagination.size, BookProps.NAME.desc()))
                .build();
    }

    @POST
    @Path("/testBookRepositoryFindAllPageSortView")
    @Api
    public Response testBookRepositoryFindAllPageSortView(Pagination pagination) {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findAll(pagination.index, pagination.size,
                Sort.by(Sort.Order.desc("name")))).build();
    }

    @GET
    @Path("/testBookRepositoryCustomQuery")
    @Api
    public Response testBookRepositoryCustomQuery(@RestQuery long id) {
        return Response.ok(bookRepository.selectBookById(id)).build();
    }

    @POST
    @Path("/testBookRepositoryFindMapByIdsView")
    @Api
    public Response testBookRepositoryFindMapByIdsView(List<Long> ids) {
        return Response.ok(bookRepository.viewer(BookDetailView.class).findMapByIds(ids)).build();
    }

    @POST
    @Path("/testBookRepositoryMerge")
    @Api
    public Response testBookRepositoryMerge(Book book) {
        return Response.ok(bookRepository.save(book, AssociatedSaveMode.MERGE)).build();
    }

    @POST
    @Path("/testBookRepositoryMergeInput")
    @Api
    public Response testBookRepositoryMergeInput(BookInput bookInput) {
        return Response.ok(bookRepository.save(bookInput, AssociatedSaveMode.MERGE)).build();
    }

    @POST
    @Path("/testBookRepositoryMergeSaveMode")
    @Api
    public Response testBookRepositoryMergeSaveMode(Book book) {
        return Response.ok(bookRepository.save(book, AssociatedSaveMode.APPEND_IF_ABSENT)).build();
    }

    @GET
    @Path("/testQuarkusOrdersSortUtilsStringCodes")
    @Api
    public Response testQuarkusOrdersSortUtilsStringCodes(@DefaultValue("id desc") @RestQuery String sort) {
        List<Book> books = Jimmer.getDefaultJSqlClient()
                .createQuery(Tables.BOOK_TABLE)
                .orderBy(QuarkusOrders.toOrders(Tables.BOOK_TABLE, SortUtils.toSort(sort)))
                .select(Tables.BOOK_TABLE)
                .execute();
        return Response.ok(books).build();
    }

    @POST
    @Path("/testEvent")
    @Transactional
    @Api
    public Response testEvent() {
        Jimmer.getDefaultJSqlClient()
                .createUpdate(Tables.BOOK_TABLE)
                .set(Tables.BOOK_TABLE.storeId(), 2L)
                .where(Tables.BOOK_TABLE.id().eq(7L))
                .execute();
        return Response.ok().build();
    }

    private static final Fetcher<Book> COMPLEX_BOOK = BOOK_FETCHER.allScalarFields()
            .store(BOOK_STORE_FETCHER.name())
            .authors(AUTHOR_FETCHER.firstName().lastName());
}
