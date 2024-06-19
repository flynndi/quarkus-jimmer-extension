package io.quarkiverse.jimmer.it.resolver;

import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.lang.Ref;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookProps;
import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.entity.BookStoreProps;
import io.quarkiverse.jimmer.it.repository.BookRepository;
import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class BookStoreNewestBooksResolver implements TransientResolver<Long, List<Long>> {

    private final BookRepository bookRepository;

    public BookStoreNewestBooksResolver(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    private JSqlClient sqlClient() {
        return bookRepository.sql();
    }

    @Override
    public Map<Long, List<Long>> resolve(Collection<Long> ids) {
        return bookRepository.findNewestIdsGroupByStoreId(ids);
    }

    @Override
    public List<Long> getDefaultValue() {
        return Collections.emptyList();
    }

    @Override
    public Ref<SortedMap<String, Object>> getParameterMapRef() {
        return sqlClient().getFilters().getTargetParameterMapRef(BookStoreProps.BOOKS);
    }

    @Nullable
    @Override
    public Collection<?> getAffectedSourceIds(@NotNull AssociationEvent e) {
        if (sqlClient().getCaches().isAffectedBy(e) && e.getImmutableProp() == BookStoreProps.BOOKS.unwrap()) {
            return Collections.singletonList(e.getSourceId());
        }
        return null;
    }

    @Nullable
    @Override
    public Collection<?> getAffectedSourceIds(@NotNull EntityEvent<?> e) {
        if (sqlClient().getCaches().isAffectedBy(e) &&
                !e.isEvict() &&
                e.getImmutableType().getJavaClass() == Book.class) {

            Ref<BookStore> storeRef = e.getUnchangedRef(BookProps.STORE);
            if (storeRef != null && storeRef.getValue() != null && e.isChanged(BookProps.EDITION)) {
                return Collections.singletonList(storeRef.getValue().id());
            }
        }
        return null;
    }
}
