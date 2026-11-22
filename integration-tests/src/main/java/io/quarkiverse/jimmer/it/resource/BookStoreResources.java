package io.quarkiverse.jimmer.it.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.client.FetchBy;
import org.babyfish.jimmer.client.meta.Api;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jboss.resteasy.reactive.RestPath;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.entity.BookFetcher;
import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.entity.BookStoreFetcher;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;

@Path("/bookStoreResource")
@Produces(MediaType.APPLICATION_JSON)
@Api
public class BookStoreResources {

    private static final Fetcher<BookStore> STORE_DETAILS = BookStoreFetcher.$
            .name()
            .avgPrice()
            .newestBooks(BookFetcher.$.name().edition());

    private final BookStoreRepository bookStoreRepository;

    public BookStoreResources(BookStoreRepository bookStoreRepository) {
        this.bookStoreRepository = bookStoreRepository;
    }

    @GET
    @Path("/{id}")
    @Api
    @Nullable
    public @FetchBy("STORE_DETAILS") BookStore getStore(@RestPath long id) {
        return bookStoreRepository.findNullable(id, STORE_DETAILS);
    }
}
