package io.quarkiverse.jimmer.deployment.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.ast.table.Props;
import org.babyfish.jimmer.sql.cache.PropCacheInvalidator;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.filter.CacheableFilter;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;

class JimmerCacheProcessorTest {

    @Test
    void registersOnlyInvalidationCallbacksIncludingInheritedAndDefaultDeclarations() throws IOException {
        var indexer = new Indexer();
        for (Class<?> type : List.of(PropCacheInvalidator.class, TransientResolver.class, CacheableFilter.class,
                DirectResolver.class, InheritedResolver.class, CallbackBase.class, CustomFilter.class,
                DefaultFilter.class, DefaultResolver.class, Unrelated.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        List<ReflectiveMethodBuildItem> registrations = new ArrayList<>();
        new JimmerCacheProcessor().registerCacheInvalidatorReflection(new CombinedIndexBuildItem(index, index),
                registrations::add);
        Set<String> expected = Stream.of(PropCacheInvalidator.class, DirectResolver.class, CallbackBase.class,
                CustomFilter.class)
                .flatMap(type -> Stream.of(EntityEvent.class, AssociationEvent.class)
                        .map(event -> type.getName() + "#getAffectedSourceIds(" + event.getName() + ")"))
                .collect(Collectors.toSet());
        assertEquals(expected, registrations.stream()
                .map(method -> method.getDeclaringClass() + "#" + method.getName()
                        + "(" + String.join(",", method.getParams()) + ")")
                .collect(Collectors.toSet()));
        assertEquals(expected.size(), registrations.size(), "Shared callback declarations should be registered once");
        assertTrue(registrations.stream().allMatch(ReflectiveMethodBuildItem::isQueryOnly),
                "Jimmer inspects declaring classes without invoking these methods reflectively");
    }

    abstract static class DirectResolver implements TransientResolver<Long, String> {
        @Override
        public Collection<?> getAffectedSourceIds(EntityEvent<?> event) {
            return null;
        }

        @Override
        public Collection<?> getAffectedSourceIds(AssociationEvent event) {
            return null;
        }

        public Collection<?> getAffectedSourceIds(String other) {
            return null;
        }

        public void otherMethod(EntityEvent<?> event) {
        }
    }

    static class CallbackBase {
        public Collection<?> getAffectedSourceIds(EntityEvent<?> event) {
            return null;
        }

        public Collection<?> getAffectedSourceIds(AssociationEvent event) {
            return null;
        }
    }

    abstract static class InheritedResolver extends CallbackBase implements TransientResolver<Long, String> {
    }

    interface CustomFilter extends CacheableFilter<Props> {
        @Override
        default Collection<?> getAffectedSourceIds(EntityEvent<?> event) {
            return null;
        }

        @Override
        default Collection<?> getAffectedSourceIds(AssociationEvent event) {
            return null;
        }
    }

    abstract static class DefaultFilter implements CustomFilter {
    }

    abstract static class DefaultResolver implements TransientResolver<Long, String> {
    }

    static class Unrelated {
        public Collection<?> getAffectedSourceIds(EntityEvent<?> event) {
            return null;
        }

        public Collection<?> getAffectedSourceIds(AssociationEvent event) {
            return null;
        }
    }
}
