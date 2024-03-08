package io.quarkiverse.jimmer.it.parser;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.parser.Context;
import io.quarkiverse.jimmer.runtime.repository.parser.QueryMethod;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;

public class QueryMethodParserTest {

    @Test
    public void testEntityMethod() throws NoSuchMethodException {

        Method method = Dao.class.getMethod("findByNameOrderByName", String.class,
                Pagination.class, Fetcher.class);
        QueryMethod queryMethod1 = QueryMethod.of(new Context(), ImmutableType.get(Book.class), method);
        System.out.println("queryMethod = " + queryMethod1);
        method = Dao.class.getMethod("findByNameAndEditionInOrderByNameAscEditionDesc", String.class, Collection.class);
        QueryMethod queryMethod = QueryMethod.of(new Context(), ImmutableType.get(Book.class), method);
        System.out.println("queryMethod1 = " + queryMethod);
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

    private static void assertQueryMethod(QueryMethod queryMethod, String expectedText) {
        Assertions.assertEquals(
                expectedText
                        .replace("\r", "")
                        .replace("\n", "")
                        .replace("--->", ""),
                queryMethod.toString());
    }
}
