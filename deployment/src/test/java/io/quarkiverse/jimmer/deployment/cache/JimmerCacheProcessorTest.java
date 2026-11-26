package io.quarkiverse.jimmer.deployment.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.ast.table.Props;
import org.babyfish.jimmer.sql.cache.PropCacheInvalidator;
import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.filter.CacheableFilter;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.jackson.deployment.IgnoreJsonDeserializeClassBuildItem;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

class JimmerCacheProcessorTest {

    @Test
    void jacksonUsesTheMessageCodecsWithoutTraversingSqlMetadata() throws Exception {
        var message = Class.forName("org.babyfish.jimmer.sql.cache.redisson.InvalidateMessage", false,
                getClass().getClassLoader());
        assertEquals("InvalidateMessageDeserializer3", message.getAnnotation(JsonDeserialize.class).using().getSimpleName());
        assertEquals("InvalidateMessageSerializer3", message.getAnnotation(JsonSerialize.class).using().getSimpleName());
        var indexer = new Indexer();
        indexer.indexClass(message);
        var index = indexer.complete();
        var ignored = new ArrayList<IgnoreJsonDeserializeClassBuildItem>();
        var types = new ArrayList<ReflectiveClassBuildItem>();
        new JimmerCacheProcessor().registerInvalidationMessageForJackson(new CombinedIndexBuildItem(index, index),
                ignored::add, types::add);
        assertEquals(List.of(message.getName()), ignored.stream().flatMap(item -> item.getDotNames().stream())
                .map(Object::toString).toList());
        assertEquals(List.of(message.getName()), types.stream().flatMap(item -> item.getClassNames().stream()).toList());
        assertFalse(types.getFirst().isConstructors());
        assertFalse(types.getFirst().isMethods());
        assertFalse(types.getFirst().isFields());
    }

    @Test
    void kryoCanConstructTheNativeInvalidationMessageWithItsOriginalFields() throws Exception {
        String name = "org.babyfish.jimmer.sql.cache.redisson.InvalidateMessage";
        var indexer = new Indexer();
        byte[] original;
        try (var input = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            original = input.readAllBytes();
        }
        indexer.index(new ByteArrayInputStream(original));
        var index = indexer.complete();
        var transforms = new ArrayList<BytecodeTransformerBuildItem>();
        var types = new ArrayList<ReflectiveClassBuildItem>();
        var methods = new ArrayList<ReflectiveMethodBuildItem>();
        var fields = new ArrayList<ReflectiveFieldBuildItem>();
        var processor = new JimmerCacheProcessor();
        processor.supportKryoInvalidationMessages(new CombinedIndexBuildItem(index, index),
                transforms::add, types::add, methods::add, fields::add);
        assertEquals(List.of(name), transforms.stream().map(BytecodeTransformerBuildItem::getClassToTransform).toList());
        assertEquals(1, types.size());
        var lookups = types.getFirst();
        assertEquals(Set.of(Collections.singleton(1L).getClass().getName(), Set.of(1L).getClass().getName(),
                Set.of(1L, 2L, 3L).getClass().getName(), UUID.class.getName()),
                Set.copyOf(lookups.getClassNames()));
        assertFalse(lookups.isConstructors());
        assertFalse(lookups.isMethods());
        assertFalse(lookups.isFields());
        assertEquals(Set.of(name, LinkedHashSet.class.getName()),
                methods.stream().map(ReflectiveMethodBuildItem::getDeclaringClass).collect(Collectors.toSet()));
        assertTrue(methods.stream().allMatch(method -> method.getName().equals("<init>") && method.getParams().length == 0));
        assertEquals(Set.of("trackerId", "typeName", "propName", "ids", "type"),
                fields.stream().map(ReflectiveFieldBuildItem::getName).collect(Collectors.toSet()));

        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        new ClassReader(original).accept(transforms.getFirst().getVisitorFunction().apply(name, writer), 0);
        byte[] transformed = writer.toByteArray();
        var type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> defineMessage() {
                return defineClass(name, transformed, 0, transformed.length);
            }
        }.defineMessage();
        var constructor = type.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertEquals(type, constructor.newInstance().getClass());

        var updated = new Indexer();
        updated.index(new ByteArrayInputStream(transformed));
        var updatedIndex = updated.complete();
        transforms.clear();
        processor.supportKryoInvalidationMessages(new CombinedIndexBuildItem(updatedIndex, updatedIndex),
                transforms::add, types::add, methods::add, fields::add);
        assertTrue(transforms.isEmpty());
    }

    @Test
    void registersInvalidatorImplementationsForInheritedAndDefaultCallbackLookup() throws IOException {
        var indexer = new Indexer();
        for (Class<?> type : List.of(PropCacheInvalidator.class, TransientResolver.class, CacheableFilter.class,
                DirectResolver.class, InheritedResolver.class, CallbackBase.class, CustomFilter.class,
                DefaultFilter.class, DefaultResolver.class, Unrelated.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        List<ReflectiveClassBuildItem> registrations = new ArrayList<>();
        new JimmerCacheProcessor().registerCacheInvalidatorReflection(new CombinedIndexBuildItem(index, index),
                registrations::add);
        assertEquals(Set.of(DirectResolver.class.getName(), InheritedResolver.class.getName(),
                DefaultFilter.class.getName(), DefaultResolver.class.getName()),
                registrations.stream().flatMap(item -> item.getClassNames().stream()).collect(Collectors.toSet()));
        for (var item : registrations) {
            assertFalse(item.isConstructors());
            assertFalse(item.isMethods());
            assertFalse(item.isFields());
        }
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
