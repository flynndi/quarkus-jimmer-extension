package io.quarkiverse.jimmer.it.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.runtime.repository.JRepository;

@ApplicationScoped
public class BookRepository implements JRepository<Book, Long> {

    public Book selectBookById(long id) {
        return sql().findById(Book.class, id);
    }

    public Map<Long, List<Long>> findNewestIdsGroupByStoreId(Collection<Long> ids) {
        return Tuple2.toMultiMap(
                sqlClient()
                        .createQuery(Tables.BOOK_TABLE)
                        .where(
                                Expression.tuple(
                                        Tables.BOOK_TABLE.name(),
                                        Tables.BOOK_TABLE.edition()).in(
                                                sqlClient().createSubQuery(Tables.BOOK_TABLE)
                                                        .where(Tables.BOOK_TABLE.storeId().in(ids))
                                                        .groupBy(Tables.BOOK_TABLE.name())
                                                        .select(
                                                                Tables.BOOK_TABLE.name(),
                                                                Tables.BOOK_TABLE.edition().max())))
                        .select(
                                Tables.BOOK_TABLE.storeId(),
                                Tables.BOOK_TABLE.id())
                        .execute());
    }
}
