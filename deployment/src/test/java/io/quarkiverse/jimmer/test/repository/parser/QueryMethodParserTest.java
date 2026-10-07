package io.quarkiverse.jimmer.test.repository.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.ast.query.OrderMode;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.parser.AndPredicate;
import io.quarkiverse.jimmer.runtime.repository.parser.Context;
import io.quarkiverse.jimmer.runtime.repository.parser.PropPredicate;
import io.quarkiverse.jimmer.runtime.repository.parser.Query;
import io.quarkiverse.jimmer.runtime.repository.parser.QueryMethod;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkiverse.jimmer.test.repository.model.Book;

@SuppressWarnings("deprecation")
public class QueryMethodParserTest {

    @Test
    public void resolvesPaginationAndFetcherWithoutConsumingPredicateParameters() throws NoSuchMethodException {
        Method method = Dao.class.getMethod("findByNameOrderByName", String.class,
                Pagination.class, Fetcher.class);
        QueryMethod queryMethod = QueryMethod.of(new Context(), ImmutableType.get(Book.class), method);

        assertEquals(1, queryMethod.getPageableParamIndex());
        assertEquals(2, queryMethod.getFetcherParamIndex());
        assertEquals(-1, queryMethod.getSortParamIndex());
        PropPredicate predicate = assertInstanceOf(PropPredicate.class, queryMethod.getQuery().getPredicate());
        assertEquals("name", predicate.getPath().toString());
        assertEquals(PropPredicate.Op.EQ, predicate.getOp());
        assertEquals(0, predicate.getParamIndex());
        assertEquals(0, predicate.getLogicParamIndex());
        List<Query.Order> orders = queryMethod.getQuery().getOrders();
        assertEquals(1, orders.size());
        assertEquals("name", orders.get(0).getPath().toString());
        assertEquals(OrderMode.ASC, orders.get(0).getOrderMode());
    }

    @Test
    public void resolvesBoxedCollectionElementsAndCompoundOrdering() throws NoSuchMethodException {
        Method method = Dao.class.getMethod("findByNameAndEditionInOrderByNameAscEditionDesc", String.class, Collection.class);
        QueryMethod queryMethod = QueryMethod.of(new Context(), ImmutableType.get(Book.class), method);

        assertEquals(-1, queryMethod.getPageableParamIndex());
        assertEquals(-1, queryMethod.getFetcherParamIndex());
        AndPredicate and = assertInstanceOf(AndPredicate.class, queryMethod.getQuery().getPredicate());
        assertEquals(2, and.getPredicates().size());
        PropPredicate name = assertInstanceOf(PropPredicate.class, and.getPredicates().get(0));
        assertEquals("name", name.getPath().toString());
        assertEquals(PropPredicate.Op.EQ, name.getOp());
        assertEquals(0, name.getParamIndex());
        PropPredicate editions = assertInstanceOf(PropPredicate.class, and.getPredicates().get(1));
        assertEquals("edition", editions.getPath().toString());
        assertEquals(int.class, editions.getPath().getType());
        assertEquals(PropPredicate.Op.IN, editions.getOp());
        assertEquals(1, editions.getParamIndex());
        assertEquals(1, editions.getLogicParamIndex());
        List<Query.Order> orders = queryMethod.getQuery().getOrders();
        assertEquals(List.of("name", "edition"), orders.stream().map(order -> order.getPath().toString()).toList());
        assertEquals(List.of(OrderMode.ASC, OrderMode.DESC), orders.stream().map(Query.Order::getOrderMode).toList());
    }

    interface Dao extends JRepository<Book, Long> {

        // Dynamic entity
        Page<Book> findByNameOrderByName(
                String name,
                Pagination pagination,
                Fetcher<Book> fetcher);

        List<Book> findByNameAndEditionInOrderByNameAscEditionDesc(
                String name,
                Collection<Integer> editions // Test boxing for element type
        );
    }
}
