package io.quarkiverse.jimmer.it.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookTable;
import io.quarkiverse.jimmer.it.entity.dto.BookDetailView;
import io.quarkiverse.jimmer.it.entity.dto.BookSpecification;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;

@Singleton
public class BookJavaRepository extends AbstractJavaRepository<Book, Long> {
    protected BookJavaRepository(JSqlClient sqlClient) {
        super(sqlClient);
    }

    public List<BookDetailView> find(BookSpecification specification) {
        BookTable book = BookTable.$;
        return sql.createQuery(book)
                .where(specification)
                .select(book.fetch(BookDetailView.class))
                .execute();
    }

    public Map<Long, BigDecimal> findAvgPriceGroupByStoreId(Collection<Long> storeIds) {
        BookTable book = BookTable.$;
        return Tuple2.toMap(sql.createQuery(book)
                .where(book.storeId().in(storeIds))
                .groupBy(book.storeId())
                .select(book.storeId(), book.price().avgAsDecimal())
                .execute());
    }
}
