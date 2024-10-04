package io.quarkiverse.jimmer.it.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.runtime.repository.JRepository;

public interface BookRepository extends JRepository<Book, Long> {

    default Book selectBookById(long id) {
        return sql().findById(Book.class, id);
    }

    default Map<Long, List<Long>> findNewestIdsGroupByStoreId(Collection<Long> ids) {
        return Tuple2.toMultiMap(
                sql()
                        .createQuery(Tables.BOOK_TABLE)
                        .where(
                                Expression.tuple(
                                        Tables.BOOK_TABLE.name(),
                                        Tables.BOOK_TABLE.edition()).in(
                                                sql().createSubQuery(Tables.BOOK_TABLE)
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
