package io.quarkiverse.jimmer.it.resolver;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.babyfish.jimmer.lang.Ref;
import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.jetbrains.annotations.NotNull;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookProps;
import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.entity.BookStoreProps;
import io.quarkiverse.jimmer.it.service.IBook;
import io.quarkiverse.jimmer.runtime.Jimmer;

@ApplicationScoped
@Named("bookStoreAvgPriceResolver")
public class BookStoreAvgPriceResolver implements TransientResolver<Long, BigDecimal> {

    private final IBook iBook;

    public BookStoreAvgPriceResolver(IBook iBook) {
        this.iBook = iBook;
    }

    @Override
    public Map<Long, BigDecimal> resolve(Collection<Long> ids) {
        return iBook.findAvgPriceGroupByStoreId(ids);
    }

    @Override
    public BigDecimal getDefaultValue() { // ❸
        return BigDecimal.ZERO;
    }

    @Override
    public Ref<SortedMap<String, Object>> getParameterMapRef() {
        return Jimmer.getDefaultJSqlClient().getFilters().getTargetParameterMapRef(BookStoreProps.BOOKS);
    }

    @Override
    public Collection<?> getAffectedSourceIds(@NotNull AssociationEvent e) {
        if (Jimmer.getDefaultJSqlClient().getCaches().isAffectedBy(e)
                && e.getImmutableProp() == BookStoreProps.BOOKS.unwrap()) {
            return Collections.singletonList(e.getSourceId());
        }
        return null;
    }

    @Override
    public Collection<?> getAffectedSourceIds(@NotNull EntityEvent<?> e) {
        if (Jimmer.getDefaultJSqlClient().getCaches().isAffectedBy(e) &&
                !e.isEvict() &&
                e.getImmutableType().getJavaClass() == Book.class) {

            Ref<BookStore> storeRef = e.getUnchangedRef(BookProps.STORE);
            if (storeRef != null && storeRef.getValue() != null && e.isChanged(BookProps.PRICE)) {
                return Collections.singletonList(storeRef.getValue().id());
            }
        }
        return null;
    }
}
