package io.quarkiverse.jimmer.deployment.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassConditionBuildItem;

class JimmerJackson3ProcessorTest {

    @Test
    void guardsOnlyJackson3CodecReflectionIncludingNestedClasses() throws IOException {
        String provider = "org.babyfish.jimmer.jackson.v3.JsonCodecProviderV3";
        String registrar = "org.babyfish.jimmer.jackson.v3.ModulesRegistrarV3$ImmutableModuleRegistrar";
        String mapper = "tools.jackson.databind.ObjectMapper";
        var indexer = new Indexer();
        for (String type : List.of(provider, registrar, "org.babyfish.jimmer.jackson.v2.JsonCodecProviderV2",
                "org.babyfish.jimmer.jackson.codec.JsonCodec")) {
            try (var input = getClass().getResourceAsStream("/" + type.replace('.', '/') + ".class")) {
                assertNotNull(input, type);
                indexer.index(input);
            }
        }
        var index = indexer.complete();
        var conditions = new ArrayList<ReflectiveClassConditionBuildItem>();
        new JimmerJackson3Processor().guardJackson3Reflection(new CombinedIndexBuildItem(index, index), conditions::add);
        assertEquals(Map.of(provider, mapper, registrar, mapper), conditions.stream()
                .collect(Collectors.toMap(ReflectiveClassConditionBuildItem::getClassName,
                        ReflectiveClassConditionBuildItem::getTypeReachable)));
    }
}
