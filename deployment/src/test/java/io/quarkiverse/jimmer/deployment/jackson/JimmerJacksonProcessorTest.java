package io.quarkiverse.jimmer.deployment.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.babyfish.jimmer.Draft;
import org.babyfish.jimmer.Dto;
import org.babyfish.jimmer.View;
import org.babyfish.jimmer.sql.fetcher.DtoMetadata;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookDraft;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;

class JimmerJacksonProcessorTest {

    @Test
    void registersGeneratedModelMetadataAndBuildersWithoutUnrelatedNestedTypes() throws IOException {
        List<Class<?>> models = List.of(CdiBook.class, NativeValue.class, NativeAddress.class, NativeBase.class,
                KotlinShape.class);
        List<Class<?>> drafts = List.of(CdiBookDraft.class, NativeValueDraft.class, NativeAddressDraft.class,
                NativeBaseDraft.class, KotlinShapeDraft.class);
        Indexer indexer = new Indexer();
        for (Class<?> draft : drafts) {
            indexer.indexClass(draft);
            for (Class<?> nested : draft.getDeclaredClasses()) {
                indexer.indexClass(nested);
            }
        }
        for (Class<?> type : List.of(Draft.class, BaseDraft.class, Ordinary.class, Ordinary.Builder.class)) {
            indexer.indexClass(type);
        }
        for (Class<?> model : models) {
            indexer.indexClass(model);
        }
        // Read these classes as bytecode: loading the V3 type would require absent Jackson 3 dependencies.
        for (String name : List.of("Doc", "Doc$Builder", "Doc$SerializerV3")) {
            try (var input = getClass().getResourceAsStream("/org/babyfish/jimmer/client/meta/" + name + ".class")) {
                assertNotNull(input);
                indexer.index(input);
            }
        }
        var index = indexer.complete();
        List<ReflectiveClassBuildItem> items = new ArrayList<>();
        List<ReflectiveFieldBuildItem> fields = new ArrayList<>();
        List<ReflectiveMethodBuildItem> methods = new ArrayList<>();
        new JimmerJacksonProcessor().registerImmutableModelReflection(new CombinedIndexBuildItem(index, index),
                items::add, fields::add, methods::add);
        Map<String, ReflectiveClassBuildItem> registrations = items.stream()
                .flatMap(item -> item.getClassNames().stream().map(name -> Map.entry(name, item)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Set<String> expected = Stream.concat(models.stream(), drafts.stream()).map(Class::getName)
                .collect(Collectors.toSet());
        drafts.stream().filter(type -> type != NativeBaseDraft.class)
                .map(type -> type.getName() + "$Builder").forEach(expected::add);
        assertEquals(expected, registrations.keySet(), "Do not register unrelated classes or nonexistent Builders");
        for (Class<?> draft : drafts) {
            var draftRegistration = registrations.get(draft.getName());
            assertTrue(draftRegistration.isClasses(), "Jimmer queries Draft.getDeclaredClasses()");
            assertFalse(draftRegistration.isConstructors());
            assertFalse(draftRegistration.isMethods());
            assertFalse(draftRegistration.isFields());
            if (draft == NativeBaseDraft.class) {
                continue;
            }
            var builderRegistration = registrations.get(draft.getName() + "$Builder");
            assertTrue(builderRegistration.isConstructors());
            assertTrue(builderRegistration.isMethods(), "Jackson must discover Builder properties and build()");
            assertFalse(builderRegistration.isFields());
            assertFalse(builderRegistration.isClasses());
        }
        for (Class<?> model : models) {
            var modelRegistration = registrations.get(model.getName());
            assertTrue(modelRegistration.isMethods(), "ImmutableProp resolves model getters reflectively");
            assertFalse(modelRegistration.isConstructors());
            assertFalse(modelRegistration.isFields());
            assertFalse(modelRegistration.isClasses());
        }
        assertEquals(Set.of(CdiBookDraft.Producer.class.getName() + "#TYPE",
                NativeValueDraft.Producer.class.getName() + "#TYPE",
                NativeAddressDraft.Producer.class.getName() + "#TYPE",
                NativeBaseDraft.Producer.class.getName() + "#TYPE",
                KotlinShapeDraft.$.class.getName() + "#INSTANCE"),
                fields.stream().map(field -> field.getDeclaringClass() + "#" + field.getName()).collect(Collectors.toSet()));
        assertEquals(1, methods.size());
        assertEquals(KotlinShapeDraft.$.class.getName(), methods.get(0).getDeclaringClass());
        assertEquals("getType", methods.get(0).getName());
        assertEquals(0, methods.get(0).getParams().length);
        assertFalse(methods.get(0).isQueryOnly(), "Metadata invokes the Kotlin producer accessor");
    }

    @Test
    void registersGeneratedDtoMetadataAndJsonPropertiesIncludingNestedProjections() throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : List.of(Dto.class, View.class, ViewShape.class, ViewShape.Nested.class,
                NonGeneratedDto.class, NonDtoWithMetadata.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        List<ReflectiveClassBuildItem> items = new ArrayList<>();
        List<ReflectiveFieldBuildItem> fields = new ArrayList<>();
        new JimmerJacksonProcessor().registerGeneratedDtoReflection(new CombinedIndexBuildItem(index, index),
                items::add, fields::add);
        Set<String> expected = Set.of(ViewShape.class.getName(), ViewShape.Nested.class.getName());
        assertEquals(expected, items.stream().flatMap(item -> item.getClassNames().stream()).collect(Collectors.toSet()));
        assertEquals(expected.stream().map(name -> name + "#METADATA").collect(Collectors.toSet()),
                fields.stream().map(field -> field.getDeclaringClass() + "#" + field.getName()).collect(Collectors.toSet()));
        for (var item : items) {
            assertTrue(item.isConstructors());
            assertTrue(item.isMethods(), "Jackson discovers DTO getters and setters behind Response/Object return types");
            assertFalse(item.isFields(), "Only the METADATA field needs explicit reflective access");
            assertFalse(item.isClasses());
        }
    }

    // Match generated View DTOs without making the test depend on the integration application's DTO build.
    private static class ViewShape implements View<CdiBook> {
        public static final DtoMetadata<CdiBook, ViewShape> METADATA = null;

        @Override
        public CdiBook toEntity() {
            return null;
        }

        public Nested getNested() {
            return null;
        }

        public static class Nested implements View<CdiBook> {
            public static final DtoMetadata<CdiBook, Nested> METADATA = null;

            @Override
            public CdiBook toEntity() {
                return null;
            }

            public String getName() {
                return null;
            }
        }
    }

    private abstract static class NonGeneratedDto implements View<CdiBook> {
    }

    private static class NonDtoWithMetadata {
        public static final DtoMetadata<CdiBook, ViewShape> METADATA = null;
    }

    private interface BaseDraft extends Draft {
    }

    private interface KotlinShape {
        String name();
    }

    // Model the KSP-generated binary structure; this is not a Kotlin native-image integration test.
    private interface KotlinShapeDraft extends KotlinShape, BaseDraft {
        class Builder {
        }

        class $ {
            public static final $ INSTANCE = new $();

            public Object getType() {
                return null;
            }
        }
    }

    private static class Ordinary {
        static class Builder {
        }
    }
}
