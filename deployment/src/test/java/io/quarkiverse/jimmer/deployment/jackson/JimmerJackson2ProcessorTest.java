package io.quarkiverse.jimmer.deployment.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassConditionBuildItem;

class JimmerJackson2ProcessorTest {

    @Test
    void guardsOnlyJackson2CodecReflectionIncludingNestedClasses() throws IOException {
        String provider = "org.babyfish.jimmer.jackson.v2.JsonCodecProviderV2";
        String registrar = "org.babyfish.jimmer.jackson.v2.ModulesRegistrarV2$ImmutableModuleRegistrar";
        String mapper = "com.fasterxml.jackson.databind.ObjectMapper";
        var indexer = new Indexer();
        for (String type : List.of(provider, registrar, "org.babyfish.jimmer.jackson.v3.JsonCodecProviderV3",
                "org.babyfish.jimmer.jackson.codec.JsonCodec")) {
            try (var input = getClass().getResourceAsStream("/" + type.replace('.', '/') + ".class")) {
                assertNotNull(input, type);
                indexer.index(input);
            }
        }
        var index = indexer.complete();
        var conditions = new ArrayList<ReflectiveClassConditionBuildItem>();
        new JimmerJackson2Processor().guardJackson2Reflection(new CombinedIndexBuildItem(index, index), conditions::add);
        assertEquals(Map.of(provider, mapper, registrar, mapper), conditions.stream()
                .collect(Collectors.toMap(ReflectiveClassConditionBuildItem::getClassName,
                        ReflectiveClassConditionBuildItem::getTypeReached)));
    }

    @Test
    void nativeBinLogKeepsAllMethodsExceptTheUnusedJackson2Helper() throws IOException {
        var transform = new JimmerJackson2Processor().pruneUnusedJackson2BinLogHelper();
        String name = transform.getClassToTransform();
        byte[] original;
        try (var input = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            original = input.readAllBytes();
        }
        var writer = new ClassWriter(0);
        new ClassReader(original).accept(transform.getVisitorFunction().apply(name, writer), 0);
        var methods = Index.singleClass(original).methods();
        assertEquals(1, methods.stream().filter(method -> method.name().equals("nodeOf")).count());
        assertEquals(methods.stream().filter(method -> !method.name().equals("nodeOf")).map(Object::toString).toList(),
                Index.singleClass(writer.toByteArray()).methods().stream().map(Object::toString).toList());
    }
}
