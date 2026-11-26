package io.quarkiverse.jimmer.deployment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.babyfish.jimmer.ClientException;
import org.babyfish.jimmer.client.ApiIgnore;
import org.babyfish.jimmer.error.CodeBasedRuntimeException;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.jackson.deployment.IgnoreJsonDeserializeClassBuildItem;

class JimmerClientProcessorTest {

    @TempDir
    Path metadataDirectory;

    @Test
    void absentGeneratorKeepsCoreJacksonProtectionWithoutGeneratorNativeRegistration() throws IOException {
        var processor = new JimmerClientProcessor();
        var availability = new JimmerClientAvailabilityBuildItem(false, false, false);
        List<IgnoreJsonDeserializeClassBuildItem> ignored = new ArrayList<>();
        List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
        List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
        List<NativeImageResourceBuildItem> resources = new ArrayList<>();
        List<ReflectiveFieldBuildItem> fields = new ArrayList<>();

        processor.excludeJimmerClientMetadataFromAutoJacksonReflection(ignored::add);
        processor.registerJimmerClientMetadataForReflection(availability, reflection::add, constructors::add);
        processor.registerClientMetadataResource(availability, resources::add);
        var index = new Indexer().complete();
        processor.registerClientApiReflection(availability, new CombinedIndexBuildItem(index, index),
                reflection::add, fields::add);

        var ignoredNames = ignored.stream().flatMap(item -> item.getDotNames().stream()).map(Object::toString).toList();
        assertTrue(ignoredNames.contains("org.babyfish.jimmer.client.meta.Doc"));
        assertTrue(ignoredNames.contains("org.babyfish.jimmer.client.meta.impl.SchemaImpl"));
        assertTrue(reflection.isEmpty());
        assertTrue(constructors.isEmpty());
        assertTrue(resources.isEmpty());
        assertTrue(fields.isEmpty());
    }

    @Test
    void programmaticGenerationKeepsMetadataResourcesAndOnlyJacksonThreeReflectionWithoutHttp() {
        var processor = new JimmerClientProcessor();
        var availability = new JimmerClientAvailabilityBuildItem(true, false, false);
        List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
        List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
        List<NativeImageResourceBuildItem> resources = new ArrayList<>();

        processor.registerJimmerClientMetadataForReflection(availability, reflection::add, constructors::add);
        processor.registerClientMetadataResource(availability, resources::add);

        assertEquals(1, reflection.size());
        var metadata = reflection.get(0);
        assertFalse(metadata.isConstructors());
        assertFalse(metadata.isMethods());
        assertFalse(metadata.isFields());
        assertTrue(metadata.getClassNames().contains("org.babyfish.jimmer.client.meta.Doc"));
        assertTrue(metadata.getClassNames().contains("org.babyfish.jimmer.client.meta.impl.SchemaImpl"));
        assertTrue(metadata.getClassNames().stream().noneMatch(name -> name.contains("$")));
        var expectedCodecs = metadata.getClassNames().stream()
                .flatMap(name -> java.util.stream.Stream.of(name + "$SerializerV3", name + "$DeserializerV3"))
                .collect(Collectors.toSet());
        assertEquals(expectedCodecs, constructors.stream().map(ReflectiveMethodBuildItem::getDeclaringClass)
                .collect(Collectors.toSet()));
        assertTrue(constructors.stream().allMatch(item -> item.getName().equals("<init>") && item.getParams().length == 0));
        assertEquals(List.of(Constant.CLIENT_RESOURCE),
                resources.stream().flatMap(item -> item.getResources().stream()).toList());
    }

    @Test
    void nativeMetadataKeepsAllDeclaredMembersExceptTheLegacyJacksonPair() throws IOException {
        List<BytecodeTransformerBuildItem> transforms = new ArrayList<>();
        new JimmerClientProcessor().pruneJackson2MetadataMembers(transforms::add);
        assertEquals(10, transforms.size());
        for (var transform : transforms) {
            String name = transform.getClassToTransform();
            byte[] original;
            try (var input = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
                original = input.readAllBytes();
            }
            var writer = new ClassWriter(0);
            new ClassReader(original).accept(transform.getVisitorFunction().apply(name, writer), 0);
            var expected = new java.util.HashSet<>(Index.singleClass(original).memberClasses());
            assertTrue(expected.remove(DotName.createSimple(name + "$SerializerV2")));
            assertTrue(expected.remove(DotName.createSimple(name + "$DeserializerV2")));
            assertEquals(expected, Index.singleClass(writer.toByteArray()).memberClasses(), name);
        }
    }

    @Test
    void registersOnlySchemaServicesAndReferencedFetcherFieldsIncludingCompanions() throws Exception {
        Indexer indexer = new Indexer();
        for (Class<?> type : List.of(ApiService.class, ApiBase.class, ApiContract.class, FetcherOwner.class,
                KotlinFetcherOwner.class, KotlinFetcherOwner.Companion.class, UnrelatedService.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        Path metadata = metadataDirectory.resolve(Constant.CLIENT_RESOURCE);
        Files.createDirectories(metadata.getParent());
        Files.writeString(metadata, """
                {"services":[{"typeName":"%s","operations":[
                  {"name":"books","returnType":{"typeName":"java.util.List","arguments":[
                    {"typeName":"Book","fetchBy":"BOOKS","fetcherOwner":"%s"}]}},
                  {"name":"another","returnType":{"typeName":"Book","fetchBy":"BOOKS","fetcherOwner":"%s"}}
                ]}],"definitions":[{"typeName":"View","props":[{"name":"books","type":
                  {"typeName":"Book","fetchBy":"BOOKS","fetcherOwner":"%s"}}]}]}
                """.formatted(ApiService.class.getName(), FetcherOwner.class.getName(), FetcherOwner.class.getName(),
                KotlinFetcherOwner.class.getName()));
        List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
        List<ReflectiveFieldBuildItem> fields = new ArrayList<>();
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (var loader = new URLClassLoader(new URL[] { metadataDirectory.toUri().toURL() }, null)) {
            Thread.currentThread().setContextClassLoader(loader);
            new JimmerClientProcessor().registerClientApiReflection(new JimmerClientAvailabilityBuildItem(true, false, false),
                    new CombinedIndexBuildItem(index, index), reflection::add, fields::add);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        assertEquals(Set.of(ApiService.class.getName(), ApiBase.class.getName(), ApiContract.class.getName()),
                reflection.stream().flatMap(item -> item.getClassNames().stream()).collect(Collectors.toSet()));
        for (var item : reflection) {
            assertFalse(item.isMethods());
            assertFalse(item.isConstructors());
            assertFalse(item.isFields());
        }
        assertEquals(Set.of(FetcherOwner.class.getName() + "#BOOKS", KotlinFetcherOwner.class.getName() + "#Companion",
                KotlinFetcherOwner.Companion.class.getName() + "#BOOKS"),
                fields.stream().map(field -> field.getDeclaringClass() + "#" + field.getName()).collect(Collectors.toSet()));
        assertEquals(3, fields.size(), "Repeated fetcher references must not duplicate registrations");
    }

    @Test
    void registersCodedExceptionGetterInvocationWithoutClientGenerationOrThrowableHierarchy() throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : List.of(CodeBasedRuntimeException.class, FailureBase.class, FieldFailure.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
        List<ReflectiveMethodBuildItem> methods = new ArrayList<>();
        new JimmerClientProcessor().registerClientExceptionGetters(new CombinedIndexBuildItem(index, index),
                reflection::add, methods::add);

        assertEquals(Set.of(FailureBase.class.getName(), FieldFailure.class.getName()),
                reflection.stream().flatMap(item -> item.getClassNames().stream()).collect(Collectors.toSet()));
        assertEquals(Set.of(FailureBase.class.getName() + "#getRequestId", FieldFailure.class.getName() + "#getDetail",
                FieldFailure.class.getName() + "#isRetryable"),
                methods.stream().map(method -> method.getDeclaringClass() + "#" + method.getName())
                        .collect(Collectors.toSet()));
        for (var item : reflection) {
            assertFalse(item.isMethods());
            assertFalse(item.isConstructors());
            assertFalse(item.isFields());
        }
    }

    private interface ApiContract {
        String inherited();
    }

    private abstract static class ApiBase {
        public String baseOperation() {
            return "base";
        }
    }

    private static class ApiService extends ApiBase implements ApiContract {
        @Override
        public String inherited() {
            return "inherited";
        }
    }

    private static class UnrelatedService {
        public String unrelated() {
            return "unrelated";
        }
    }

    private static class FetcherOwner {
        private static final Fetcher<?> BOOKS = null;
        private static final Fetcher<?> UNUSED = null;
    }

    // Match the field layout used by FetchedTypeImpl's Kotlin fallback without depending on Kotlin SQL.
    private static class KotlinFetcherOwner {
        private static final Companion Companion = new Companion();

        private static class Companion {
            private final Fetcher<?> BOOKS = null;
        }
    }

    @ClientException(family = "NATIVE_TEST", subTypes = FieldFailure.class)
    public abstract static class FailureBase extends CodeBasedRuntimeException {
        public String getRequestId() {
            return "request";
        }
    }

    @ClientException(code = "FIELD_FAILURE")
    public static class FieldFailure extends FailureBase {
        public String getDetail() {
            return "detail";
        }

        public boolean isRetryable() {
            return true;
        }

        @JsonIgnore
        public String getJsonIgnored() {
            return "ignored";
        }

        @ApiIgnore
        public String getApiIgnored() {
            return "ignored";
        }

        public static String getStatic() {
            return "static";
        }

        public String getWithParameter(String value) {
            return value;
        }

        public String getChecked() throws IOException {
            return "checked";
        }

        public void getVoid() {
        }

        public String getlowerCase() {
            return "not a getter";
        }
    }
}
