package io.quarkiverse.jimmer.deployment.client;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.babyfish.jimmer.ClientException;
import org.babyfish.jimmer.impl.util.StringUtil;
import org.jboss.jandex.DotName;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Jackson2Present;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiRecorder;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptRecorder;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import io.quarkus.jackson.deployment.IgnoreJsonDeserializeClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;
import tools.jackson.databind.JsonNode;

final class JimmerClientProcessor {

    // org.babyfish.jimmer.client.meta(.impl) classes that carry both a Jackson-2 (V2) and a Jackson-3 (V3)
    // serializer/deserializer as nested classes.
    private static final String[] JIMMER_CLIENT_METADATA_CLASSES = {
            "org.babyfish.jimmer.client.meta.Doc",
            "org.babyfish.jimmer.client.meta.TypeName",
            "org.babyfish.jimmer.client.meta.impl.ApiOperationImpl",
            "org.babyfish.jimmer.client.meta.impl.ApiParameterImpl",
            "org.babyfish.jimmer.client.meta.impl.ApiServiceImpl",
            "org.babyfish.jimmer.client.meta.impl.EnumConstantImpl",
            "org.babyfish.jimmer.client.meta.impl.PropImpl",
            "org.babyfish.jimmer.client.meta.impl.SchemaImpl",
            "org.babyfish.jimmer.client.meta.impl.TypeDefinitionImpl",
            "org.babyfish.jimmer.client.meta.impl.TypeRefImpl",
    };

    @BuildStep
    JimmerClientAvailabilityBuildItem clientAvailability(Capabilities capabilities) {
        // jimmer-client is a library, not a Quarkus extension: an extension capability cannot identify its presence.
        return new JimmerClientAvailabilityBuildItem(
                QuarkusClassLoader.isClassPresentAtRuntime("org.babyfish.jimmer.client.runtime.Metadata"),
                capabilities.isPresent(Capability.VERTX_HTTP),
                QuarkusClassLoader.isClassPresentAtRuntime("jakarta.ws.rs.Path")
                        && QuarkusClassLoader.isClassPresentAtRuntime("org.jboss.resteasy.reactive.RestMulti"));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerRoutes(JimmerBuildTimeConfig config, JimmerClientAvailabilityBuildItem availability,
            LaunchModeBuildItem launchMode,
            TypeScriptRecorder ts, OpenApiRecorder openapi,
            BuildProducer<RouteBuildItem> routes, BuildProducer<JimmerClientEndpointBuildItem> registries) {
        if (!config.enable()) {
            return;
        }
        // Arc's validation step may run later than route construction; reject invalid paths before using them here.
        JimmerConfigValidator.validateBuildTime(config);
        var client = config.client();
        if (client.ts().path().isEmpty() && client.openapi().path().isEmpty()) {
            return;
        }
        Set<String> keys = new LinkedHashSet<>();
        client.ts().path().ifPresent(path -> keys.add("quarkus.jimmer.client.ts.path"));
        client.openapi().path().ifPresent(path -> keys.add("quarkus.jimmer.client.openapi.path"));
        if (!availability.generatorAvailable()) {
            throw new ConfigurationException("Configured Jimmer client endpoints " + keys
                    + " require org.babyfish.jimmer:jimmer-client; add this dependency with the same Jimmer version "
                    + "as the extension or remove these paths", keys);
        }
        if (!availability.httpAvailable()) {
            throw new ConfigurationException("Configured Jimmer client endpoints " + keys + " require quarkus-vertx-http "
                    + "(also supplied by quarkus-rest); add an HTTP extension or remove these paths", keys);
        }
        // The metadata adapter reads JAX-RS declarations even when routes are served directly by Vert.x.
        if (!availability.metadataApisAvailable()) {
            throw new ConfigurationException("Configured Jimmer client endpoints " + keys
                    + " require JAX-RS and Quarkus REST metadata APIs used by the current client metadata adapter "
                    + "(normally supplied by quarkus-rest or quarkus-rest-client); add a compatible extension "
                    + "or remove these paths", keys);
        }
        client.ts().path().ifPresent(path -> {
            routes.produce(RouteBuildItem.newManagementRoute(path).withRouteCustomizer(ts.route())
                    .withRoutePathConfigKey("quarkus.jimmer.client.ts.path").withRequestHandler(ts.getHandler(config))
                    .asBlockingRoute().build());
            registries.produce(
                    new JimmerClientEndpointBuildItem("TypeScriptResource", JimmerHttpPaths.managementUrl(path, launchMode)));
        });
        client.openapi().path().ifPresent(path -> {
            routes.produce(RouteBuildItem.newManagementRoute(path).withRouteCustomizer(openapi.route())
                    .withRoutePathConfigKey("quarkus.jimmer.client.openapi.path")
                    .withRequestHandler(openapi.getHandler(config)).asBlockingRoute().build());
            registries.produce(
                    new JimmerClientEndpointBuildItem("OpenApiResource", JimmerHttpPaths.managementUrl(path, launchMode)));
        });
    }

    @BuildStep
    void excludeJimmerClientMetadataFromAutoJacksonReflection(
            BuildProducer<IgnoreJsonDeserializeClassBuildItem> ignoredClasses) {
        // Jimmer core ships metadata for both Jackson versions. Select the Jackson 3 implementations below
        // instead of walking both hierarchies when indexing these types, including in headless applications.
        for (String metadataClass : JIMMER_CLIENT_METADATA_CLASSES) {
            ignoredClasses.produce(new IgnoreJsonDeserializeClassBuildItem(DotName.createSimple(metadataClass)));
        }
    }

    @BuildStep
    void registerJimmerClientMetadataForReflection(JimmerClientAvailabilityBuildItem availability,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<ReflectiveMethodBuildItem> constructors) {
        if (!availability.generatorAvailable()) {
            return;
        }
        // Jackson inspects annotations on the metadata types and instantiates their custom codecs.
        // The codecs construct the metadata objects themselves.
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(JIMMER_CLIENT_METADATA_CLASSES)
                .constructors(false).reason("Jimmer client schema annotations").build());
        for (String metadataClass : JIMMER_CLIENT_METADATA_CLASSES) {
            constructors.produce(new ReflectiveMethodBuildItem("Jimmer client schema serialization",
                    metadataClass + "$SerializerV3", "<init>"));
            constructors.produce(new ReflectiveMethodBuildItem("Jimmer client schema deserialization",
                    metadataClass + "$DeserializerV3", "<init>"));
        }
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class, onlyIfNot = Jackson2Present.class)
    void pruneJackson2MetadataMembers(BuildProducer<BytecodeTransformerBuildItem> transformers) {
        // GraalVM 25 enumerates declared classes for every registered type. Jimmer 0.12.5 lists
        // Jackson 2 serializers as members of the same metadata types that Jackson 3 must reflect on.
        for (String metadataClass : JIMMER_CLIENT_METADATA_CLASSES) {
            transformers.produce(new BytecodeTransformerBuildItem(metadataClass, (name, visitor) -> {
                String owner = name.replace('.', '/');
                Set<String> legacyMembers = Set.of(owner + "$SerializerV2", owner + "$DeserializerV2");
                return new ClassVisitor(Opcodes.ASM9, visitor) {
                    @Override
                    public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        if (!legacyMembers.contains(name)) {
                            super.visitInnerClass(name, outerName, innerName, access);
                        }
                    }

                    @Override
                    public void visitNestMember(String nestMember) {
                        if (!legacyMembers.contains(nestMember)) {
                            super.visitNestMember(nestMember);
                        }
                    }
                };
            }));
        }
    }

    @BuildStep(onlyIf = Enabled.class)
    void unwrapClientResponseTypes(JimmerClientAvailabilityBuildItem availability,
            BuildProducer<GeneratedResourceBuildItem> resources,
            BuildProducer<NativeImageResourceBuildItem> nativeResources) throws java.io.IOException {
        if (!availability.generatorAvailable()) {
            return;
        }
        var schemas = JimmerClientMetadata.read(Thread.currentThread().getContextClassLoader());
        boolean changed = false;
        for (var schema : schemas) {
            changed |= JimmerClientMetadata.unwrapRestResponses(schema);
        }
        if (!changed) {
            return;
        }
        // Keep schemas separate and in their original order: Jimmer filters by groups before resolving
        // duplicate services and definitions. Merging here would change that runtime selection.
        List<String> paths = new ArrayList<>();
        for (int index = 0; index < schemas.size(); index++) {
            String path = "META-INF/quarkus-jimmer/client/" + index + ".json";
            paths.add(path);
            resources.produce(new GeneratedResourceBuildItem(path, JimmerClientMetadata.write(schemas.get(index))));
        }
        resources.produce(new GeneratedResourceBuildItem(Constant.CLIENT_SCHEMA_INDEX,
                String.join("\n", paths).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        paths.add(Constant.CLIENT_SCHEMA_INDEX);
        nativeResources.produce(new NativeImageResourceBuildItem(paths));
    }

    @BuildStep(onlyIf = Enabled.class)
    void registerClientApiReflection(JimmerClientAvailabilityBuildItem availability,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflection,
            BuildProducer<ReflectiveFieldBuildItem> fields) throws IOException {
        if (!availability.generatorAvailable()) {
            return;
        }
        var schemas = JimmerClientMetadata.read(Thread.currentThread().getContextClassLoader());
        var index = combinedIndex.getComputingIndex();
        var pending = new ArrayDeque<DotName>();
        var fetcherTypes = new ArrayList<JsonNode>();
        for (var schema : schemas) {
            for (JsonNode service : schema.path("services")) {
                pending.add(DotName.createSimple(service.path("typeName").asString("")));
            }
            fetcherTypes.addAll(schema.findParents("fetchBy"));
        }
        var registered = new HashSet<DotName>();
        while (!pending.isEmpty()) {
            var name = pending.removeFirst();
            if (name.equals(DotName.OBJECT_NAME) || !registered.add(name)) {
                continue;
            }
            var service = index.getClassByName(name);
            if (service == null) {
                continue;
            }
            // MetadataBuilder enumerates public methods, including inherited API declarations, without invoking them.
            reflection.produce(ReflectiveClassBuildItem.builder(name.toString())
                    .constructors(false).reason("Jimmer client API method discovery").build());
            if (service.superName() != null) {
                pending.add(service.superName());
            }
            pending.addAll(service.interfaceNames());
        }
        var registeredFields = new HashSet<String>();
        for (JsonNode type : fetcherTypes) {
            var owner = index.getClassByName(DotName.createSimple(type.path("fetcherOwner").asString("")));
            if (owner == null) {
                continue;
            }
            String fieldName = type.path("fetchBy").asString("");
            var field = owner.field(fieldName);
            if (field != null && registeredFields.add(owner.name() + "#" + fieldName)) {
                fields.produce(new ReflectiveFieldBuildItem(field));
            }
            // Kotlin fetchers can instead live on the owner's Companion instance.
            var companion = owner.field("Companion");
            if (companion != null) {
                var companionType = index.getClassByName(companion.type().name());
                var companionFetcher = companionType != null ? companionType.field(fieldName) : null;
                if (companionFetcher != null) {
                    if (registeredFields.add(owner.name() + "#Companion")) {
                        fields.produce(new ReflectiveFieldBuildItem(companion));
                    }
                    if (registeredFields.add(companionType.name() + "#" + fieldName)) {
                        fields.produce(new ReflectiveFieldBuildItem(companionFetcher));
                    }
                }
            }
        }
    }

    @BuildStep(onlyIf = Enabled.class)
    void registerClientExceptionGetters(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflection,
            BuildProducer<ReflectiveMethodBuildItem> methods) {
        var index = combinedIndex.getIndex();
        for (String base : List.of("org.babyfish.jimmer.error.CodeBasedException",
                "org.babyfish.jimmer.error.CodeBasedRuntimeException")) {
            for (var exception : index.getAllKnownSubclasses(DotName.createSimple(base))) {
                if (exception.declaredAnnotation(DotName.createSimple(ClientException.class)) == null) {
                    continue;
                }
                // Exception construction discovers getters even without the optional client generator.
                reflection.produce(ReflectiveClassBuildItem.builder(exception.name().toString())
                        .constructors(false).reason("Jimmer coded exception getter discovery").build());
                for (var method : exception.methods()) {
                    if (!Modifier.isPublic(method.flags()) || Modifier.isStatic(method.flags())
                            || method.parametersCount() != 0 || !method.typeParameters().isEmpty()
                            || !method.exceptions().isEmpty() || method.returnType().name().toString().equals("void")
                            || method.name().equals("getFamily") || method.name().equals("getCode")
                            || method.hasAnnotation(DotName.createSimple("com.fasterxml.jackson.annotation.JsonIgnore"))
                            || method.hasAnnotation(DotName.createSimple("org.babyfish.jimmer.client.ApiIgnore"))
                            || StringUtil.propName(method.name(),
                                    method.returnType().name().toString().equals("boolean")) == null) {
                        continue;
                    }
                    methods.produce(new ReflectiveMethodBuildItem("Jimmer coded exception field access", method));
                }
            }
        }
    }

    @BuildStep(onlyIf = Enabled.class)
    void registerClientMetadataResource(JimmerClientAvailabilityBuildItem availability,
            BuildProducer<NativeImageResourceBuildItem> resources) {
        if (availability.generatorAvailable()) {
            resources.produce(new NativeImageResourceBuildItem(Constant.CLIENT_RESOURCE));
        }
    }
}
