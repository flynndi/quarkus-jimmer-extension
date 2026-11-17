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

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
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
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.jackson.deployment.IgnoreJsonDeserializeClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.spi.RouteBuildItem;

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

    @BuildStep(onlyIf = Enabled.class)
    void excludeJimmerClientMetadataFromAutoJacksonReflection(
            BuildProducer<IgnoreJsonDeserializeClassBuildItem> ignoredClasses) {
        // Each class below declares both a Jackson-2 (V2) and a Jackson-3 (V3) serializer/deserializer as
        // nested classes. Quarkus's automatic Jackson reflection registration walks the *entire* declared-class
        // hierarchy (allDeclaredClasses) of every Jackson-annotated type it finds, which drags in the V3 nested
        // classes even though only Jackson 2 is on the runtime classpath. Loading those V3 classes during the
        // native-image build fails with NoClassDefFoundError (tools.jackson.databind.ValueSerializer/ValueDeserializer
        // are absent), so these classes are excluded from that automatic walk; the members actually needed are
        // registered explicitly below when generation is available. These metadata types live in jimmer-core,
        // so this exclusion must also apply when the optional jimmer-client dependency is absent.
        for (String metadataClass : JIMMER_CLIENT_METADATA_CLASSES) {
            ignoredClasses.produce(new IgnoreJsonDeserializeClassBuildItem(DotName.createSimple(metadataClass)));
        }
    }

    @BuildStep(onlyIf = Enabled.class)
    void registerJimmerClientMetadataForReflection(JimmerClientAvailabilityBuildItem availability,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        if (!availability.generatorAvailable()) {
            return;
        }
        List<String> classes = new ArrayList<>();
        for (String metadataClass : JIMMER_CLIENT_METADATA_CLASSES) {
            classes.add(metadataClass);
            classes.add(metadataClass + "$SerializerV2");
            classes.add(metadataClass + "$DeserializerV2");
        }
        classes.add("org.babyfish.jimmer.client.meta.Doc$Builder");
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(classes.toArray(new String[0]))
                .constructors()
                .build());
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
                pending.add(DotName.createSimple(service.path("typeName").asText()));
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
                    .constructors(false).queryMethods().reason("Jimmer client API method discovery").build());
            if (service.superName() != null) {
                pending.add(service.superName());
            }
            pending.addAll(service.interfaceNames());
        }
        var registeredFields = new HashSet<String>();
        for (JsonNode type : fetcherTypes) {
            var owner = index.getClassByName(DotName.createSimple(type.path("fetcherOwner").asText()));
            if (owner == null) {
                continue;
            }
            String fieldName = type.path("fetchBy").asText();
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
                        .constructors(false).queryMethods().reason("Jimmer coded exception getter discovery").build());
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
                    methods.produce(new ReflectiveMethodBuildItem("Jimmer coded exception field access", false, method));
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
