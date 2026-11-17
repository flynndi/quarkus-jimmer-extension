package io.quarkiverse.jimmer.deployment.jackson;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.babyfish.jimmer.Draft;
import org.babyfish.jimmer.Dto;
import org.babyfish.jimmer.internal.FixedInputField;
import org.babyfish.jimmer.jackson.JsonConverter;
import org.babyfish.jimmer.sql.fetcher.DtoMetadata;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.RemovedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyIgnoreWarningBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
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

    @BuildStep
    AdditionalApplicationArchiveMarkerBuildItem indexImmutableLibraries() {
        // Include generated models in dependency jars, including non-entity immutable and embeddable types.
        return new AdditionalApplicationArchiveMarkerBuildItem(Constant.IMMUTABLES_RESOURCE);
    }

    @BuildStep
    void registerImmutableModelReflection(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflection,
            BuildProducer<ReflectiveFieldBuildItem> fields,
            BuildProducer<ReflectiveMethodBuildItem> methods) {
        var index = combinedIndex.getIndex();
        var models = new ArrayDeque<DotName>();
        for (var draft : index.getAllKnownSubinterfaces(DotName.createSimple(Draft.class))) {
            String draftName = draft.name().toString();
            String builderName = draftName + "$Builder";
            var builder = index.getClassByName(DotName.createSimple(builderName));
            var javaProducer = index.getClassByName(DotName.createSimple(draftName + "$Producer"));
            var kotlinProducer = index.getClassByName(DotName.createSimple(draftName + "$$"));
            if (builder == null && javaProducer == null && kotlinProducer == null) {
                continue;
            }
            // Both Jackson builder discovery and ImmutableType lookup enumerate the Draft's nested types.
            reflection.produce(ReflectiveClassBuildItem.builder(draftName)
                    .constructors(false).classes().reason("Jimmer generated model discovery").build());
            if (builder != null) {
                reflection.produce(ReflectiveClassBuildItem.builder(builderName)
                        .constructors().methods().reason("Jimmer Jackson builder deserialization").build());
            }
            if (draftName.endsWith("Draft")) {
                var model = index.getClassByName(DotName.createSimple(draftName.substring(0, draftName.length() - 5)));
                if (model != null) {
                    models.add(model.name());
                    // ImmutableProp resolves the model's getters, including for models with no REST endpoint.
                    reflection.produce(ReflectiveClassBuildItem.builder(model.name().toString())
                            .constructors(false).methods().reason("Jimmer immutable property metadata").build());
                }
            }
            // Metadata.create reads Java Producer.TYPE or Kotlin $.INSTANCE.getType(), also for mapped superclasses.
            if (javaProducer != null && javaProducer.field("TYPE") != null) {
                fields.produce(new ReflectiveFieldBuildItem(javaProducer.field("TYPE")));
            }
            if (kotlinProducer != null) {
                if (kotlinProducer.field("INSTANCE") != null) {
                    fields.produce(new ReflectiveFieldBuildItem(kotlinProducer.field("INSTANCE")));
                }
                if (kotlinProducer.method("getType") != null) {
                    methods.produce(new ReflectiveMethodBuildItem(kotlinProducer.method("getType")));
                }
            }
        }
        var inspectedModels = new HashSet<DotName>();
        var registeredEnums = new HashSet<DotName>();
        while (!models.isEmpty()) {
            var modelName = models.removeFirst();
            if (!inspectedModels.add(modelName)) {
                continue;
            }
            var model = combinedIndex.getComputingIndex().getClassByName(modelName);
            if (model == null) {
                continue;
            }
            models.addAll(model.interfaceNames());
            for (var method : model.methods()) {
                if (Modifier.isPublic(method.flags()) && !Modifier.isStatic(method.flags())
                        && method.parametersCount() == 0 && (method.isAbstract()
                                || method.hasDeclaredAnnotation(DotName.createSimple("org.babyfish.jimmer.Formula")))) {
                    registerEnumFields(method.returnType(), combinedIndex.getComputingIndex(), registeredEnums, fields);
                }
            }
        }
    }

    private static void registerEnumFields(Type type, IndexView index, Set<DotName> registeredEnums,
            BuildProducer<ReflectiveFieldBuildItem> fields) {
        switch (type.kind()) {
            case ARRAY -> registerEnumFields(type.asArrayType().component(), index, registeredEnums, fields);
            case PARAMETERIZED_TYPE -> type.asParameterizedType().arguments()
                    .forEach(argument -> registerEnumFields(argument, index, registeredEnums, fields));
            case WILDCARD_TYPE -> {
                var bound = type.asWildcardType().extendsBound();
                if (bound != null) {
                    registerEnumFields(bound, index, registeredEnums, fields);
                }
            }
            case CLASS -> {
                var enumType = index.getClassByName(type.name());
                if (enumType != null && enumType.isEnum() && registeredEnums.add(enumType.name())) {
                    // ScalarProviderManager reads every constant to resolve @EnumItem, even with no annotations.
                    for (var field : enumType.fields()) {
                        if (field.isEnumConstant()) {
                            fields.produce(new ReflectiveFieldBuildItem(field));
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    @BuildStep
    void registerJsonConverterConstructors(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveMethodBuildItem> methods) {
        var converterNames = new HashSet<DotName>();
        // Include composed annotations: Jimmer also resolves @JsonConverter on annotation types.
        for (var annotation : combinedIndex.getIndex().getAnnotations(DotName.createSimple(JsonConverter.class))) {
            converterNames.add(annotation.value().asClass().name());
        }
        for (var converterName : converterNames) {
            var converter = combinedIndex.getComputingIndex().getClassByName(converterName);
            if (converter == null || Modifier.isAbstract(converter.flags())) {
                continue;
            }
            var constructor = converter.method("<init>");
            // ConverterMetadata constructs converters directly, independently of CDI or Jackson serializers.
            if (constructor != null && Modifier.isPublic(constructor.flags())) {
                methods.produce(new ReflectiveMethodBuildItem(constructor));
            }
        }
    }

    @BuildStep
    void registerGeneratedDtoReflection(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflection,
            BuildProducer<ReflectiveFieldBuildItem> fields) {
        for (var dto : combinedIndex.getIndex().getAllKnownImplementors(DotName.createSimple(Dto.class))) {
            var metadata = dto.field("METADATA");
            if (metadata == null || !Modifier.isStatic(metadata.flags()) || !Modifier.isFinal(metadata.flags())
                    || !metadata.type().name().equals(DotName.createSimple(DtoMetadata.class))) {
                continue;
            }
            // Repository/view queries discover this field even when the DTO is hidden behind a REST Response.
            fields.produce(new ReflectiveFieldBuildItem(metadata));
            // TypeScript generation queries this annotation to distinguish required nullable input fields.
            for (var field : dto.fields()) {
                if (field.hasAnnotation(DotName.createSimple(FixedInputField.class))) {
                    fields.produce(new ReflectiveFieldBuildItem(field));
                }
            }
            // Generated DTOs expose getters/setters; nested projections are also Dto implementations.
            // Jackson's @JsonDeserialize processing handles input DTO builders separately.
            reflection.produce(ReflectiveClassBuildItem.builder(dto.name().toString())
                    .constructors().methods().reason("Jimmer generated DTO JSON mapping").build());
        }
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
