package io.quarkiverse.jimmer.runtime.repository.common;

import org.babyfish.jimmer.Page;

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
     * @param page must not be {@literal null}.
     * @return a page of entities
     */
    Page<T> findAll(io.quarkiverse.jimmer.runtime.repository.support.Page page);
}
