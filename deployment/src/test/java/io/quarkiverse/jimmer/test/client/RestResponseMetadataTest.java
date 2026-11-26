package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.babyfish.jimmer.client.meta.TypeName;
import org.babyfish.jimmer.client.runtime.ListType;
import org.babyfish.jimmer.client.runtime.NullableType;
import org.babyfish.jimmer.client.runtime.ObjectType;
import org.babyfish.jimmer.client.runtime.Operation;
import org.babyfish.jimmer.client.runtime.impl.MetadataBuilder;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.Metadatas;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiDocumentGenerator;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptGenerator;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.config.SmallRyeConfigBuilder;

class RestResponseMetadataTest {

    private static final String PAYLOAD = TypeName.of(Payload.class).toString(true);
    private static final String LIBRARY_PAYLOAD = TypeName.of(LibraryPayload.class).toString(true);

    @RegisterExtension
    static final QuarkusExtensionTest APP = new QuarkusExtensionTest()
            .withApplicationRoot(
                    archive -> archive.addClasses(Api.class, Payload.class, DuplicateApi.class, SharedPayload.class)
                            .addAsResource(new StringAsset(schema(Api.class, "app", PAYLOAD,
                                    operation("wrapped", response(type(PAYLOAD))) + ','
                                            + operation("items", response("{\"typeName\":\"java.util.List\",\"arguments\":["
                                                    + type(PAYLOAD) + "]}"))
                                            + ','
                                            + operation("empty", response(type("java.lang.Void"))) + ','
                                            + operation("plain", type(PAYLOAD)) + ','
                                            + operation("nullable", response("{\"typeName\":\"" + PAYLOAD
                                                    + "\",\"nullable\":true}")))),
                                    Constant.CLIENT_RESOURCE))
            .withAdditionalDependency(archive -> archive.addClasses(LibraryApi.class, LibraryPayload.class)
                    .addAsResource(new StringAsset(schema(LibraryApi.class, "library", LIBRARY_PAYLOAD,
                            operation("library", response(type(LIBRARY_PAYLOAD))))), Constant.CLIENT_RESOURCE))
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-rest-jackson", Version.getVersion())))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:rest-response-metadata");

    @Test
    void unwrapsApplicationAndDependencyReturnTypesBeforeResolvingMetadata() {
        for (boolean generics : new boolean[] { false, true }) {
            var metadata = Metadatas.create(generics, null, null);
            Map<String, Operation> operations = metadata.getServices().stream()
                    .flatMap(service -> service.getOperations().stream())
                    .collect(Collectors.toMap(Operation::getName, operation -> operation));
            assertEquals(Payload.class, ((ObjectType) operations.get("wrapped").getReturnType()).getJavaType());
            assertEquals(Payload.class, ((ObjectType) ((ListType) operations.get("items").getReturnType())
                    .getElementType()).getJavaType());
            assertNull(operations.get("empty").getReturnType());
            assertEquals(operations.get("plain").getReturnType(), operations.get("wrapped").getReturnType());
            assertEquals(Payload.class, ((ObjectType) ((NullableType) operations.get("nullable").getReturnType())
                    .getTargetType()).getJavaType());
            assertEquals(LibraryPayload.class, ((ObjectType) operations.get("library").getReturnType()).getJavaType());
            assertTrue(metadata.getStaticTypes().stream()
                    .noneMatch(type -> type.getJavaType().getName().equals(RestResponse.class.getName())));
        }
    }

    @Test
    void openApiAndTypeScriptDescribeBodiesAndPreserveGroups() throws IOException {
        JimmerBuildTimeConfig config = new SmallRyeConfigBuilder().addDefaultInterceptors()
                .withMapping(JimmerBuildTimeConfig.class).build().getConfigMapping(JimmerBuildTimeConfig.class);
        for (String group : new String[] { "app", "library" }) {
            String openApi = new String(OpenApiDocumentGenerator.generate(Metadatas.create(false, group, "/v1"), config),
                    StandardCharsets.UTF_8);
            assertFalse(openApi.contains("/RestResponse_") || openApi.contains("/RestResponse'"), openApi);
            assertTrue(openApi.contains(group.equals("app") ? "/v1/app/wrapped" : "/v1/library"), openApi);
            assertFalse(openApi.contains(group.equals("app") ? "/v1/library" : "/v1/app/wrapped"), openApi);
            StringBuilder typeScript = new StringBuilder();
            try (var zip = new ZipInputStream(new ByteArrayInputStream(
                    TypeScriptGenerator.generate(Metadatas.create(true, group, null), config.client().ts())))) {
                while (zip.getNextEntry() != null) {
                    typeScript.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            assertFalse(typeScript.toString().contains("RestResponse<"), typeScript.toString());
            assertTrue(typeScript.toString().contains("Promise<"), typeScript.toString());
        }
    }

    @Test
    void preservesPerResourceGroupFilteringAndDuplicatePrecedence() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        for (String groups : new String[] { null, "public", "admin", "public,admin" }) {
            // Compare against Jimmer's original resource loading, including its different group rules
            // for services and definitions. A single merged schema would not preserve these rules.
            var originalSchema = MetadataBuilder.loadSchema(groups == null ? null : Set.of(groups.split(",")));
            var metadata = Metadatas.create(false, groups, null);
            var service = metadata.getServices().stream().filter(it -> it.getJavaType() == DuplicateApi.class).findFirst();
            assertEquals(originalSchema.getApiServiceMap().containsKey(TypeName.of(DuplicateApi.class)), service.isPresent(),
                    "groups=" + groups);
            if (service.isPresent()) {
                var bodyType = (ObjectType) service.get().getOperations().get(0).getReturnType();
                assertEquals(SharedPayload.class, bodyType.getJavaType());
                assertEquals(originalSchema.getTypeDefinitionMap().get(TypeName.of(SharedPayload.class)).getPropMap().keySet(),
                        bodyType.getProperties().keySet(), "groups=" + groups);
            }
            assertSame(original, Thread.currentThread().getContextClassLoader());
        }
    }

    private static String schema(Class<?> api, String group, String payload, String operations) {
        String duplicateGroups = group.equals("app") ? "[\"public\",\"admin\"]" : "[\"public\"]";
        String definitionGroups = group.equals("app") ? "[\"admin\"]" : "[\"public\"]";
        String property = group.equals("app") ? "first" : "second";
        return "{\"services\":[{\"typeName\":\"" + TypeName.of(api).toString(true)
                + "\",\"groups\":[\"" + group + "\"],\"operations\":[" + operations
                + "]},{\"typeName\":\"" + TypeName.of(DuplicateApi.class).toString(true)
                + "\",\"groups\":" + duplicateGroups + ",\"operations\":["
                + operation("duplicate", response(type(TypeName.of(SharedPayload.class).toString(true))))
                + "]}],\"definitions\":[{\"typeName\":\"" + payload
                + "\",\"kind\":\"OBJECT\",\"props\":[{\"name\":\"name\",\"type\":{\"typeName\":\"java.lang.String\"}}]},"
                + "{\"typeName\":\"" + TypeName.of(SharedPayload.class).toString(true)
                + "\",\"kind\":\"OBJECT\",\"groups\":" + definitionGroups + ",\"props\":[{\"name\":\""
                + property + "\",\"type\":{\"typeName\":\"java.lang.String\"}}]}]}";
    }

    private static String operation(String name, String returnType) {
        return "{\"name\":\"" + name + "\",\"key\":\"" + name
                + "\",\"parameters\":[],\"returnType\":" + returnType + '}';
    }

    private static String response(String body) {
        return "{\"typeName\":\"org.jboss.resteasy.reactive.RestResponse\",\"arguments\":[" + body + "]}";
    }

    private static String type(String name) {
        return "{\"typeName\":\"" + name + "\"}";
    }

    @Path("/app")
    public interface Api {
        @GET
        @Path("/wrapped")
        RestResponse<Payload> wrapped();

        @GET
        @Path("/items")
        RestResponse<List<Payload>> items();

        @GET
        @Path("/empty")
        RestResponse<Void> empty();

        @GET
        @Path("/plain")
        Payload plain();

        @GET
        @Path("/nullable")
        RestResponse<Payload> nullable();
    }

    @Path("/library")
    public interface LibraryApi {
        @GET
        RestResponse<LibraryPayload> library();
    }

    @Path("/duplicate")
    public interface DuplicateApi {
        @GET
        RestResponse<SharedPayload> duplicate();
    }

    public record Payload(String name) {
    }

    public record LibraryPayload(String name) {
    }

    public record SharedPayload(String first, String second) {
    }
}
