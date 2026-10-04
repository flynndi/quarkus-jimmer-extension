package io.quarkiverse.jimmer.deployment.jackson;

import java.util.HashSet;
import java.util.Set;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.RemovedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyIgnoreWarningBuildItem;
import io.quarkus.jackson.spi.ClassPathJacksonModuleBuildItem;
import io.quarkus.maven.dependency.ArtifactKey;

final class JimmerJacksonProcessor {

    private static final String JIMMER_JACKSON_MODULE = "org.babyfish.jimmer.jackson.v2.ImmutableModuleV2";

    @BuildStep
    void registerJimmerJacksonModule(BuildProducer<ClassPathJacksonModuleBuildItem> classPathJacksonModules) {
        if (!QuarkusClassLoader.isClassPresentAtRuntime(JIMMER_JACKSON_MODULE)) {
            return;
        }
        classPathJacksonModules.produce(new ClassPathJacksonModuleBuildItem(JIMMER_JACKSON_MODULE));
    }

    // org.babyfish.jimmer.jackson.v3.* (jimmer-core) is Jimmer's Jackson-3 codec implementation. It is never
    // used at runtime here (Quarkus manages Jackson 2), but Quarkus's reflective-hierarchy registration expands
    // any registered org.babyfish.jimmer.jackson.codec.* interface (JsonCodec, JsonReader, JsonWriter,
    // JsonConverter, JsonTypeFactory) to every implementation found in the Jandex index, including these V3
    // ones. Those V3 classes reference tools.jackson.databind types absent from the classpath, so registering
    // them for native-image reflection fails with NoClassDefFoundError. Removing the .class files from the
    // augmentation/native-image classpath entirely keeps them out of the index so they can never be reached.
    private static final String[] JIMMER_JACKSON_V3_CLASSES = {
            "ImmutableAnnotationIntrospectorV3",
            "ImmutableAnnotationIntrospectorV3$1",
            "ImmutableAnnotationIntrospectorV3$2",
            "ImmutableModuleV3",
            "ImmutablePropertyWriterV3",
            "ImmutableSerializerModifierV3",
            "JacksonUtilsV3",
            "JsonCodecProviderV3",
            "JsonCodecV3",
            "JsonConverterV3",
            "JsonReaderV3",
            "JsonTypeFactoryV3",
            "JsonWriterV3",
            "ModulesRegistrarV3",
            "ModulesRegistrarV3$ImmutableModuleRegistrar",
            "ModulesRegistrarV3$KotlinModuleRegistrar",
            "NodePropertiesIteratorV3",
            "NodeV3",
    };

    @BuildStep(onlyIf = Enabled.class)
    RemovedResourceBuildItem removeJimmerJacksonV3Classes() {
        Set<String> resources = new HashSet<>();
        for (String simpleName : JIMMER_JACKSON_V3_CLASSES) {
            resources.add("org/babyfish/jimmer/jackson/v3/" + simpleName + ".class");
        }
        return new RemovedResourceBuildItem(ArtifactKey.of("org.babyfish.jimmer", "jimmer-core"), resources);
    }

    @BuildStep(onlyIf = Enabled.class)
    ReflectiveHierarchyIgnoreWarningBuildItem ignoreJackson3ReflectionWarnings() {
        // Jimmer bundles Jackson 3 support classes in the same artifacts as the Jackson 2 runtime used by Quarkus.
        // When Quarkus scans Jackson 2 annotations, it can reach those unused Jackson 3 signatures and warn.
        return new ReflectiveHierarchyIgnoreWarningBuildItem(dotName -> dotName.toString().startsWith("tools.jackson."));
    }
}
