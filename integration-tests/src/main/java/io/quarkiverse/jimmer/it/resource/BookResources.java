package io.quarkiverse.jimmer.it.resource;

import java.math.BigDecimal;
import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.client.FetchBy;
import org.babyfish.jimmer.client.meta.Api;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.config.error.UserInfoException;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookProps;
import io.quarkiverse.jimmer.it.entity.Fetchers;
import io.quarkiverse.jimmer.it.entity.dto.BookDetailView;
import io.quarkiverse.jimmer.it.entity.dto.BookSpecification;
import io.quarkiverse.jimmer.it.repository.BookJavaRepository;
import io.quarkiverse.jimmer.runtime.repo.PageParam;

@Path("/bookResource")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api
public class BookResources implements Fetchers {

    private final BookJavaRepository bookRepository;

    public BookResources(BookJavaRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @GET
    @Path("/book/{id}")
    @Api
    public @FetchBy("COMPLEX_BOOK") Book getBookByIdFetcher(@RestPath long id) {
        return bookRepository.findById(id, COMPLEX_BOOK);
    }

    @GET
    @Path("/book/{id}/view")
    @Api
    public RestResponse<BookDetailView> getBookView(@RestPath long id) {
        BookDetailView view = bookRepository.findById(id, BookDetailView.class);
        return view == null ? RestResponse.noContent() : RestResponse.ok(view);
    }

    @GET
    @Path("/books/page")
    @Api
    public Page<@FetchBy("SIMPLE_BOOK") Book> getBookPage(
            @RestQuery @DefaultValue("0") int index, @RestQuery @DefaultValue("10") int size) {
        if (index < 0 || size < 1) {
            throw new BadRequestException("index must be non-negative and size must be positive");
        }
        return bookRepository.findPage(PageParam.byIndex(index, size), SIMPLE_BOOK, BookProps.ID.desc());
    }

    @POST
    @Path("/book")
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Book postBook(Book book) {
        return bookRepository.save(book).getModifiedEntity();
    }

    @GET
    @Path("/books/search")
    @Api
    public List<BookDetailView> searchBooks(@RestQuery @Nullable String name,
            @RestQuery @Nullable BigDecimal minPrice, @RestQuery @Nullable BigDecimal maxPrice,
            @RestQuery @Nullable String storeName, @RestQuery @Nullable String authorName) {
        BookSpecification specification = new BookSpecification();
        specification.setName(name);
        specification.setMinPrice(minPrice);
        specification.setMaxPrice(maxPrice);
        specification.setStoreName(storeName);
        specification.setAuthorName(authorName);
        return bookRepository.find(specification);
    }

    @GET
    @Path("/testError")
    @Api
    public void testError() throws UserInfoException.IllegalUserName {
        throw UserInfoException.illegalUserName("testError", List.of('a'));
    }

    private static final Fetcher<Book> SIMPLE_BOOK = BOOK_FETCHER.name();

    private static final Fetcher<Book> COMPLEX_BOOK = BOOK_FETCHER.allScalarFields()
            .store(BOOK_STORE_FETCHER.name())
            .authors(AUTHOR_FETCHER.firstName().lastName());
}
