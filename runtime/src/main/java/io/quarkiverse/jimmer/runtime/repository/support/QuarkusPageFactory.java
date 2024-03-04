package io.quarkiverse.jimmer.runtime.repository.support;

import java.util.List;

import org.babyfish.jimmer.sql.ast.impl.query.PageSource;
import org.babyfish.jimmer.sql.ast.query.PageFactory;

public class QuarkusPageFactory<E> implements PageFactory<E, Page<E>> {

    private static final QuarkusPageFactory<?> INSTANCE = new QuarkusPageFactory<>();

    private QuarkusPageFactory() {
    }

    @Override
    public Page<E> create(List<E> entities, long totalCount, PageSource source) {
        return new Page<>(
                entities,

                source.getPageIndex(),
                source.getPageSize(),
                Utils.toSort(
                        source.getOrders(),
                        source.getSqlClient().getMetadataStrategy()),
                totalCount);
    }

    @SuppressWarnings("unchecked")
    public static <E> QuarkusPageFactory<E> getInstance() {
        return (QuarkusPageFactory<E>) INSTANCE;
    }
}
