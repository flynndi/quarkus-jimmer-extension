package io.quarkiverse.jimmer.runtime.repository.support;

import java.util.List;

import io.quarkiverse.jimmer.runtime.repository.common.Sort;

public class Page<E> {

    /**
     * The current page index (0-based).
     */
    public final int index;

    /**
     * The current page size;
     */
    public final int size;

    private long total;

    public List<E> list;

    public Sort sort;

    /**
     * Builds a page of the given size.
     *
     * @param size the page size
     * @throws IllegalArgumentException if the page size is less than or equal to 0
     * @see #ofSize(int)
     */
    public Page(int size) {
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
    public Page(int index, int size) {
        if (index < 0)
            throw new IllegalArgumentException("Page index must be >= 0 : " + index);
        if (size <= 0)
            throw new IllegalArgumentException("Page size must be > 0 : " + size);
        this.index = index;
        this.size = size;
    }

    public Page(int index, int size, List<E> list) {
        if (index < 0)
            throw new IllegalArgumentException("Page index must be >= 0 : " + index);
        if (size <= 0)
            throw new IllegalArgumentException("Page size must be > 0 : " + size);
        this.index = index;
        this.size = size;
        this.list = list;
    }

    public Page(int index, int size, long total, List<E> list) {
        if (index < 0)
            throw new IllegalArgumentException("Page index must be >= 0 : " + index);
        if (size <= 0)
            throw new IllegalArgumentException("Page size must be > 0 : " + size);
        this.index = index;
        this.size = size;
        this.total = total;
        this.list = list;
    }

    public Page(int index, int size, Sort sort) {
        if (index < 0)
            throw new IllegalArgumentException("Page index must be >= 0 : " + index);
        if (size <= 0)
            throw new IllegalArgumentException("Page size must be > 0 : " + size);
        this.index = index;
        this.size = size;
        this.sort = sort;
    }

    public Page(List<E> list, int index, int size, Sort sort, long total) {
        if (index < 0)
            throw new IllegalArgumentException("Page index must be >= 0 : " + index);
        if (size <= 0)
            throw new IllegalArgumentException("Page size must be > 0 : " + size);
        this.index = index;
        this.size = size;
        this.total = total;
        this.list = list;
        this.sort = sort;
    }

    /**
     * Builds a page of the given index and size.
     *
     * @param index the page index (0-based)
     * @param size the page size
     * @throws IllegalArgumentException if the page index is less than 0
     * @throws IllegalArgumentException if the page size is less than or equal to 0
     */
    public static Page of(int index, int size) {
        return new Page(index, size);
    }

    public static Page of(int index, int size, Sort sort) {
        return new Page(index, size, sort);
    }

    /**
     * Builds a page of the given size.
     *
     * @param size the page size
     * @throws IllegalArgumentException if the page size is less than or equal to 0
     */
    public static Page ofSize(int size) {
        return new Page(size);
    }

    /**
     * Returns a new page with the next page index and the same size.
     *
     * @return a new page with the next page index and the same size.
     * @see #previous()
     */
    public Page next() {
        return new Page(index + 1, size);
    }

    /**
     * Returns a new page with the previous page index and the same size, or this page if it is the first page.
     *
     * @return a new page with the next page index and the same size, or this page if it is the first page.
     * @see #next()
     */
    public Page previous() {
        return index > 0 ? new Page(index - 1, size) : this;
    }

    /**
     * Returns a new page with the first page index (0) and the same size, or this page if it is the first page.
     *
     * @return a new page with the first page index (0) and the same size, or this page if it is the first page.
     */
    public Page first() {
        return index > 0 ? new Page(0, size) : this;
    }

    /**
     * Returns a new page at the given page index and the same size, or this page if the page index is the same.
     *
     * @param newIndex the new page index
     * @return a new page at the given page index and the same size, or this page if the page index is the same.
     */
    public Page index(int newIndex) {
        return newIndex != index ? new Page(newIndex, size) : this;
    }
}
