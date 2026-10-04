package io.quarkiverse.jimmer.deployment.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.jackson.deployment.IgnoreJsonDeserializeClassBuildItem;

class JimmerClientProcessorTest {

    @Test
    void absentGeneratorKeepsCoreJacksonProtectionWithoutGeneratorNativeRegistration() {
        var processor = new JimmerClientProcessor();
        var availability = new JimmerClientAvailabilityBuildItem(false, false, false);
        List<IgnoreJsonDeserializeClassBuildItem> ignored = new ArrayList<>();
        List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
        List<NativeImageResourceBuildItem> resources = new ArrayList<>();

        processor.excludeJimmerClientMetadataFromAutoJacksonReflection(ignored::add);
        processor.registerJimmerClientMetadataForReflection(availability, reflection::add);
        processor.registerClientMetadataResource(availability, resources::add);

        var ignoredNames = ignored.stream().flatMap(item -> item.getDotNames().stream()).map(Object::toString).toList();
        assertTrue(ignoredNames.contains("org.babyfish.jimmer.client.meta.Doc"));
        assertTrue(ignoredNames.contains("org.babyfish.jimmer.client.meta.impl.SchemaImpl"));
        assertTrue(reflection.isEmpty());
        assertTrue(resources.isEmpty());
    }

    @Test
    void programmaticGenerationKeepsMetadataResourcesAndOnlyJacksonTwoReflectionWithoutHttp() {
        var processor = new JimmerClientProcessor();
        var availability = new JimmerClientAvailabilityBuildItem(true, false, false);
        List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
        List<NativeImageResourceBuildItem> resources = new ArrayList<>();

        processor.registerJimmerClientMetadataForReflection(availability, reflection::add);
        processor.registerClientMetadataResource(availability, resources::add);

        assertEquals(1, reflection.size());
        var metadata = reflection.get(0);
        assertTrue(metadata.isConstructors());
        assertFalse(metadata.isClasses(), "Walking all nested types would include the unavailable Jackson 3 serializers");
        assertTrue(metadata.getClassNames().contains("org.babyfish.jimmer.client.meta.Doc$Builder"));
        assertTrue(metadata.getClassNames().contains("org.babyfish.jimmer.client.meta.impl.SchemaImpl$SerializerV2"));
        assertTrue(metadata.getClassNames().contains("org.babyfish.jimmer.client.meta.impl.SchemaImpl$DeserializerV2"));
        assertTrue(metadata.getClassNames().stream().noneMatch(name -> name.endsWith("V3")));
        assertEquals(List.of(Constant.CLIENT_RESOURCE),
                resources.stream().flatMap(item -> item.getResources().stream()).toList());
    }
}
