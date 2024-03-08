package io.quarkiverse.jimmer.runtime.repository.common;

import org.babyfish.jimmer.Page;

import io.quarkiverse.jimmer.runtime.repository.support.Pagination;

@Deprecated
public interface PagingAndSortingRepository<T, ID> extends CrudRepository<T, ID> {

    /**
     * Returns all entities sorted by the given options.
     *
     * @param sort the {@link Sort} specification to sort the results by, can be {@link Sort#unsorted()}, must not be
     *        {@literal null}.
     * @return all entities sorted by the given options
     */
    Iterable<T> findAll(Sort sort);

    /**
     * Returns a {@link Page}
     *
     * @param pagination must not be {@literal null}.
     * @return a page of entities
     */
    Page<T> findAll(Pagination pagination);
}
