package io.quarkiverse.jimmer.it.graphql;

import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import graphql.schema.DataFetchingEnvironment;
import io.quarkiverse.jimmer.generated.graphql.model.BookGql;
import io.quarkiverse.jimmer.generated.graphql.model.BookStoreGql;
import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkiverse.jimmer.it.repository.BookStoreRepository;
import io.quarkiverse.jimmer.runtime.graphql.DataFetchingEnvironments;
import io.quarkiverse.jimmer.runtime.graphql.facade.JimmerGraphQLFacades;
import io.smallrye.graphql.api.Context;

@GraphQLApi
public class JimmerGraphQLApi {

    private final BookRepository bookRepository;

    private final BookStoreRepository bookStoreRepository;

    public JimmerGraphQLApi(BookRepository bookRepository, BookStoreRepository bookStoreRepository) {
        this.bookRepository = bookRepository;
        this.bookStoreRepository = bookStoreRepository;
    }

    @Query
    public BookGql book(@Name("id") long id, Context context) {
        DataFetchingEnvironment env = context.unwrap(DataFetchingEnvironment.class);
        Fetcher<Book> fetcher = DataFetchingEnvironments.createFetcher(Book.class, env);
        Book book = bookRepository.findNullable(id, fetcher);
        return JimmerGraphQLFacades.wrap(book, BookGql.class);
    }

    @Query
    public BookStoreGql bookStore(@Name("id") long id, Context context) {
        DataFetchingEnvironment env = context.unwrap(DataFetchingEnvironment.class);
        Fetcher<BookStore> fetcher = DataFetchingEnvironments.createFetcher(BookStore.class, env);
        BookStore bookStore = bookStoreRepository.findNullable(id, fetcher);
        return JimmerGraphQLFacades.wrap(bookStore, BookStoreGql.class);
    }
}
