package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.babyfish.jimmer.View;
import org.babyfish.jimmer.meta.ImmutableType;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.repository.parser.AndPredicate;
import io.quarkiverse.jimmer.runtime.repository.parser.Context;
import io.quarkiverse.jimmer.runtime.repository.parser.OrPredicate;
import io.quarkiverse.jimmer.runtime.repository.parser.Predicate;
import io.quarkiverse.jimmer.runtime.repository.parser.PropPredicate;
import io.quarkiverse.jimmer.runtime.repository.parser.Query;
import io.quarkiverse.jimmer.runtime.repository.parser.QueryMethod;
import io.quarkiverse.jimmer.runtime.repository.parser.Source;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.ParserEntity;

class RepositoryParserCompatibilityTest {

    @Test
    void keepsConnectorWordsInsidePropertyNames() {
        assertProperty(query("findByNameAndromeda").getPredicate(), "nameAndromeda");
        assertProperty(query("findByChildOrganization").getPredicate(), "childOrganization");
    }

    @Test
    void stillRecognizesConnectorsBeforeAnUppercasePropertyName() {
        AndPredicate and = assertInstanceOf(AndPredicate.class,
                query("findByNameAndChildOrganization").getPredicate());
        assertEquals(2, and.getPredicates().size());
        assertProperty(and.getPredicates().get(0), "name");
        assertProperty(and.getPredicates().get(1), "childOrganization");

        OrPredicate or = assertInstanceOf(OrPredicate.class,
                query("findByNameOrChildOrganization").getPredicate());
        assertEquals(2, or.getPredicates().size());
        assertProperty(or.getPredicates().get(0), "name");
        assertProperty(or.getPredicates().get(1), "childOrganization");
    }

    @Test
    void resolvesDirectViewEntityTypeForAListReturn() throws NoSuchMethodException {
        QueryMethod method = bookMethod(DirectViewQueries.class, "findByName", String.class);

        assertEquals(DirectBookView.class, method.getViewType());
        assertEquals(-1, method.getViewTypeParamIndex());
        PropPredicate predicate = assertProperty(method.getQuery().getPredicate(), "name");
        assertEquals(0, predicate.getParamIndex());
    }

    @Test
    void resolvesViewEntityTypeThroughAGenericSuperclass() throws NoSuchMethodException {
        QueryMethod method = bookMethod(InheritedViewQueries.class, "findFirstByName", String.class);

        assertEquals(CdiBookView.class, method.getViewType());
        assertEquals(-1, method.getViewTypeParamIndex());
        assertEquals(1, method.getQuery().getLimit());
        assertProperty(method.getQuery().getPredicate(), "name");
    }

    @Test
    void retainsDynamicViewTypeParameters() throws NoSuchMethodException {
        QueryMethod method = bookMethod(DynamicViewQueries.class, "findByName", String.class, Class.class);

        assertNull(method.getViewType());
        assertEquals(1, method.getViewTypeParamIndex());
        PropPredicate predicate = assertProperty(method.getQuery().getPredicate(), "name");
        assertEquals(0, predicate.getParamIndex());
    }

    @Test
    void rejectsAViewOfADifferentEntity() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> bookMethod(WrongViewQueries.class, "findByName", String.class));

        assertTrue(exception.getMessage().contains("returned element type"));
        assertTrue(exception.getMessage().contains(CdiBook.class.getName()));
    }

    private static Query query(String name) {
        return Query.of(new Context(), new Source(name), ImmutableType.get(ParserEntity.class));
    }

    private static QueryMethod bookMethod(Class<?> declaringType, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = declaringType.getMethod(name, parameterTypes);
        return QueryMethod.of(new Context(), ImmutableType.get(CdiBook.class), method);
    }

    private static PropPredicate assertProperty(Predicate predicate, String name) {
        PropPredicate property = assertInstanceOf(PropPredicate.class, predicate);
        assertEquals(name, property.getPath().toString());
        assertEquals(PropPredicate.Op.EQ, property.getOp());
        return property;
    }

    interface DirectViewQueries {
        List<DirectBookView> findByName(String name);
    }

    interface InheritedViewQueries {
        CdiBookView findFirstByName(String name);
    }

    interface DynamicViewQueries {
        <V extends View<CdiBook>> List<V> findByName(String name, Class<V> viewType);
    }

    interface WrongViewQueries {
        List<OtherEntityView> findByName(String name);
    }

    public record DirectBookView(CdiBook entity) implements View<CdiBook> {
        @Override
        public CdiBook toEntity() {
            return entity;
        }
    }

    public static class GenericView<T> implements View<T> {
        private final T entity;

        public GenericView(T entity) {
            this.entity = entity;
        }

        @Override
        public T toEntity() {
            return entity;
        }
    }

    public static final class CdiBookView extends GenericView<CdiBook> {
        public CdiBookView(CdiBook entity) {
            super(entity);
        }
    }

    public static final class OtherEntityView extends GenericView<ParserEntity> {
        public OtherEntityView(ParserEntity entity) {
            super(entity);
        }
    }
}
