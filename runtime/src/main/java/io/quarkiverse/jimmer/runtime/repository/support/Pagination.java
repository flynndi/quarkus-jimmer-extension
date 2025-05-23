package io.quarkiverse.jimmer.runtime.repository.support;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Pagination {

    /**
     * The current page index (0-based).
     */
    public final int index;

    /**
     * The current page size;
     */
    public final int size;

    /**
     * Builds a page of the given size.
     *
     * @param size the page size
     * @throws IllegalArgumentException if the page size is less than or equal to 0
     * @see #ofSize(int)
     */
    public Pagination(int size) {
        this(0, size);
    }

    /**
     * Builds a page of the given index and size.
     *
     * @param index the page index (0-based)
     * @param size the page size
     * @throws IllegalArgumentException if the page index is less than 0
     * @throws IllegalArgumentException if the page size is less than or equal to 0
     * @see #of(int, int)
     */
    @JsonCreator
    public Pagination(int index, int size) {
        if (index < 0)
            throw new IllegalArgumentException("Page index must be >= 0 : " + index);
        if (size <= 0)
            throw new IllegalArgumentException("Page size must be > 0 : " + size);
        this.index = index;
        this.size = size;
    }

    /**
     * Builds a page of the given index and size.
     *
     * @param index the page index (0-based)
     * @param size the page size
     * @throws IllegalArgumentException if the page index is less than 0
     * @throws IllegalArgumentException if the page size is less than or equal to 0
     */
    public static Pagination of(int index, int size) {
        return new Pagination(index, size);
    }

    /**
     * Builds a page of the given size.
     *
     * @param size the page size
     * @throws IllegalArgumentException if the page size is less than or equal to 0
     */
    public static Pagination ofSize(int size) {
        return new Pagination(size);
    }

    /**
     * Returns a new page with the next page index and the same size.
     *
     * @return a new page with the next page index and the same size.
     * @see #previous()
     */
    public Pagination next() {
        return new Pagination(index + 1, size);
    }

    /**
     * Returns a new page with the previous page index and the same size, or this page if it is the first page.
     *
     * @return a new page with the next page index and the same size, or this page if it is the first page.
     * @see #next()
     */
    public Pagination previous() {
        return index > 0 ? new Pagination(index - 1, size) : this;
    }

    /**
     * Returns a new page with the first page index (0) and the same size, or this page if it is the first page.
     *
     * @return a new page with the first page index (0) and the same size, or this page if it is the first page.
     */
    public Pagination first() {
        return index > 0 ? new Pagination(0, size) : this;
    }

    /**
     * Returns a new page at the given page index and the same size, or this page if the page index is the same.
     *
     * @param newIndex the new page index
     * @return a new page at the given page index and the same size, or this page if the page index is the same.
     */
    public Pagination index(int newIndex) {
        return newIndex != index ? new Pagination(newIndex, size) : this;
    }
}
