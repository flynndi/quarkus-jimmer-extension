package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;

import org.babyfish.jimmer.client.meta.TypeName;
import org.babyfish.jimmer.client.runtime.Metadata;
import org.babyfish.jimmer.client.runtime.NullableType;
import org.babyfish.jimmer.client.runtime.Operation;
import org.babyfish.jimmer.client.runtime.Parameter;
import org.babyfish.jimmer.client.runtime.VirtualType;
import org.babyfish.jimmer.client.runtime.impl.IllegalApiException;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestHeader;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.jimmer.runtime.client.Metadatas;

class MetadatasTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void modelsStandardAndQuarkusBindingsWithoutInventingDefaults() throws IOException {
        Map<String, Operation> operations = operations(Api.class);
        var standard = operations.get("standard").getParameters();
        assertEquals("id", standard.get(0).getPathVariable());
        assertFalse(standard.get(0).getType() instanceof NullableType);
        assertEquals("search", standard.get(1).getRequestParam());
        assertTrue(standard.get(1).getType() instanceof NullableType);
        assertNull(standard.get(1).getDefaultValue());
        assertEquals("X-Trace", standard.get(2).getRequestHeader());
        assertNull(standard.get(2).getDefaultValue());
        assertTrue(standard.stream().noneMatch(Parameter::isRequestBody));

        var quarkus = operations.get("quarkus").getParameters();
        assertEquals("id", quarkus.get(0).getPathVariable());
        assertEquals("search", quarkus.get(1).getRequestParam());
        assertEquals("Content-Type", quarkus.get(2).getRequestHeader());
        assertEquals("X-Request-Id", quarkus.get(3).getRequestHeader());
        assertTrue(quarkus.stream().noneMatch(Parameter::isRequestBody));
    }

    @Test
    void distinguishesDefaultsRequiredParametersAndNullableBodies() throws IOException {
        Map<String, Operation> operations = operations(Api.class);
        var defaults = operations.get("defaults").getParameters();
        assertEquals("20", defaults.get(0).getDefaultValue());
        assertTrue(defaults.get(0).getType() instanceof NullableType);
        assertEquals("", defaults.get(1).getDefaultValue());
        assertTrue(defaults.get(1).getType() instanceof NullableType);
        assertFalse(defaults.get(2).getType() instanceof NullableType);

        Parameter body = operations.get("body").getParameters().get(0);
        assertTrue(body.isRequestBody());
        assertFalse(body.getType() instanceof NullableType);
        Parameter nullableBody = operations.get("nullableBody").getParameters().get(0);
        assertTrue(nullableBody.isRequestBody());
        assertTrue(nullableBody.getType() instanceof NullableType);
        Parameter implicitBody = operations(ImplicitApi.class).get("body").getParameters().get(0);
        assertTrue(implicitBody.isRequestBody());
        assertFalse(implicitBody.getType() instanceof NullableType);
    }

    @Test
    void usesMethodConsumesAndNamedMultipartParts() throws IOException {
        Map<String, Operation> operations = operations(Api.class);
        var parts = operations.get("upload").getParameters();
        assertEquals("upload", parts.get(0).getRequestPart());
        assertEquals(VirtualType.FILE, ((NullableType) parts.get(0).getType()).getTargetType());
        assertEquals("caption", parts.get(1).getRequestPart());
        assertTrue(parts.stream().noneMatch(Parameter::isRequestBody));
        assertTrue(operations.get("methodConsumes").getParameters().get(0).isRequestBody());
    }

    @Test
    void omitsFrameworkContextParameters() throws IOException {
        var parameters = operations(Api.class).get("context").getParameters();
        assertEquals(1, parameters.size());
        assertEquals("search", parameters.get(0).getRequestParam());
    }

    @Test
    void rejectsUnsupportedFormAndBodyMediaTypes() {
        IllegalApiException formFailure = assertThrows(IllegalApiException.class, () -> operations(UrlEncodedApi.class));
        assertTrue(formFailure.getMessage().contains("multipart/form-data"));
        IllegalApiException bodyFailure = assertThrows(IllegalApiException.class, () -> operations(TextApi.class));
        assertTrue(bodyFailure.getMessage().contains("application/json"));
    }

    private Map<String, Operation> operations(Class<?> api) throws IOException {
        // The fixture is the same resource consumed by the pinned Jimmer MetadataBuilder.
        // Keep this isolated from any API metadata on the deployment test classpath.
        Path schema = Files.createTempFile(temporaryDirectory, "jimmer-client-", ".json");
        String methods = Arrays.stream(api.getDeclaredMethods()).map(MetadatasTest::operationSchema)
                .collect(Collectors.joining(","));
        Files.writeString(schema, "{\"services\":[{\"typeName\":\"" + TypeName.of(api).toString(true)
                + "\",\"operations\":[" + methods + "]}],\"definitions\":[]}");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = new ClassLoader(previous) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals("META-INF/jimmer/client")) {
                    return Collections.enumeration(Collections.singleton(schema.toUri().toURL()));
                }
                if (name.equals("META-INF/jimmer/doc.properties")) {
                    return Collections.emptyEnumeration();
                }
                return super.getResources(name);
            }
        };
        Thread.currentThread().setContextClassLoader(loader);
        try {
            Metadata metadata = Metadatas.create(false, null, null);
            return metadata.getServices().get(0).getOperations().stream()
                    .collect(Collectors.toMap(operation -> operation.getJavaMethod().getName(), operation -> operation));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    private static String operationSchema(Method method) {
        StringBuilder key = new StringBuilder(method.getName());
        StringBuilder parameters = new StringBuilder();
        var javaParameters = method.getParameters();
        for (int i = 0; i < javaParameters.length; i++) {
            var parameter = javaParameters[i];
            key.append(':').append(parameter.getType().getName());
            if (i != 0) {
                parameters.append(',');
            }
            parameters.append("{\"name\":\"").append(parameter.getName()).append("\",\"type\":{\"typeName\":\"")
                    .append(parameter.getType().getName()).append('"');
            if (parameter.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                parameters.append(",\"nullable\":true");
            }
            parameters.append("},\"index\":").append(i).append('}');
        }
        return "{\"name\":\"" + method.getName() + "\",\"key\":\"" + key
                + "\",\"parameters\":[" + parameters + "],\"returnType\":{\"typeName\":\"java.lang.String\"}}";
    }

    @jakarta.ws.rs.Path("/api")
    @Consumes("application/json")
    public interface Api {
        @GET
        @jakarta.ws.rs.Path("/standard/{id}")
        String standard(@PathParam("id") long id, @QueryParam("search") String search,
                @HeaderParam("X-Trace") String trace);

        @GET
        @jakarta.ws.rs.Path("/quarkus/{id}")
        String quarkus(@RestPath long id, @RestQuery String search, @RestHeader String contentType,
                @RestHeader("X-Request-Id") String requestId);

        @GET
        @jakarta.ws.rs.Path("/defaults")
        String defaults(@QueryParam("limit") @DefaultValue("20") int limit,
                @RestHeader("X-Mode") @DefaultValue("") String mode, @RestQuery @NonNull String required);

        @POST
        @jakarta.ws.rs.Path("/body")
        String body(String body);

        @POST
        @jakarta.ws.rs.Path("/nullable-body")
        String nullableBody(@Nullable String body);

        @POST
        @Consumes("application/json; charset=UTF-8")
        @jakarta.ws.rs.Path("/method-consumes")
        String methodConsumes(String body);

        @POST
        @Consumes("multipart/form-data")
        @jakarta.ws.rs.Path("/upload")
        String upload(@RestForm("upload") FileUpload file, @FormParam("caption") String caption);

        @GET
        @jakarta.ws.rs.Path("/context")
        String context(@Context UriInfo uriInfo, jakarta.ws.rs.core.Configuration configuration,
                jakarta.ws.rs.container.ResourceInfo resourceInfo, io.vertx.ext.web.RoutingContext routingContext,
                io.vertx.core.http.HttpServerRequest request, io.vertx.core.http.HttpServerResponse response,
                org.jboss.resteasy.reactive.server.spi.ServerRequestContext serverContext,
                org.jboss.resteasy.reactive.server.SimpleResourceInfo simpleResourceInfo,
                @RestQuery String search);
    }

    public interface ImplicitApi {
        @POST
        @jakarta.ws.rs.Path("/implicit-body")
        String body(String body);
    }

    public interface UrlEncodedApi {
        @POST
        @Consumes("application/x-www-form-urlencoded")
        @jakarta.ws.rs.Path("/form")
        String form(@RestForm String value);
    }

    public interface TextApi {
        @POST
        @Consumes("text/plain")
        @jakarta.ws.rs.Path("/text")
        String text(String value);
    }
}
