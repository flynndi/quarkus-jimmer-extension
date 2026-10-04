package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.babyfish.jimmer.client.meta.TypeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerOpenApiConfig;
import io.quarkiverse.jimmer.runtime.client.Metadatas;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiDocumentGenerator;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiRecorder;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptGenerator;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptRecorder;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.smallrye.config.SmallRyeConfigBuilder;

class ClientResourceHandlersTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedDocumentsUseExplicitConfigWithoutLookingUpCdi() throws IOException {
        withMetadata(() -> {
            HttpTestResponse openApi = new HttpTestResponse();
            new OpenApiRecorder().getHandler(config()).handle(openApi.context(Map.of()));
            assertTrue(openApi.body.toString().contains("Recorded HTTP API"));
            assertEquals(Constant.APPLICATION_YML, openApi.headers.get("content-type"));
            HttpTestResponse ts = new HttpTestResponse();
            new TypeScriptRecorder().getHandler(config()).handle(ts.context(Map.of()));
            assertNotNull(ts.body);
            assertEquals(Constant.APPLICATION_ZIP, ts.headers.get("content-type"));
        });
    }

    @Test
    void generatesOpenApiWithoutAnHttpRequestAndPreservesDocumentConfiguration() throws IOException {
        withMetadata(() -> {
            JimmerBuildTimeConfig config = config(Map.of(
                    "quarkus.jimmer.client.openapi.properties.info.title", "图书接口",
                    "quarkus.jimmer.client.openapi.properties.info.description", "公开图书查询",
                    "quarkus.jimmer.client.openapi.properties.servers[0].url", "https://{environment}.example/api",
                    "quarkus.jimmer.client.openapi.properties.servers[0].variables.environment.defaultValue", "dev",
                    "quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.type", "apiKey",
                    "quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.name", "X-Api-Key",
                    "quarkus.jimmer.client.openapi.properties.securities[0].token", "read",
                    "quarkus.jimmer.error-translator.http-status", "422"));
            String document = new String(OpenApiDocumentGenerator.generate(Metadatas.create(false, "public", "/v1"), config),
                    StandardCharsets.UTF_8);

            assertTrue(document.contains("图书接口"));
            assertTrue(document.contains("公开图书查询"));
            assertTrue(document.contains("/v1/resource/read:"));
            assertFalse(document.contains("/admin/read:"));
            assertTrue(document.contains("https://{environment}.example/api"));
            assertTrue(document.contains("default: dev"));
            assertTrue(document.contains("securitySchemes:"));
            assertTrue(document.contains("name: 'X-Api-Key'"), document);
            assertTrue(document.contains("in: header"), document);
            assertFalse(document.contains("flows:"), document);
            assertTrue(document.contains("security:"), document);
            assertTrue(document.matches("(?s).*\\n\\s*['\"]?422['\"]?:\\s*\\n.*"), document);
            assertTrue(document.contains("EXPECTED_FAILURE"), document);
        });
    }

    @Test
    void fillsMissingInfoDefaultsAndOmitsUnconfiguredOptionalNodes() throws IOException {
        withMetadata(() -> {
            for (Map<String, String> values : List.of(Map.<String, String> of(),
                    Map.of("quarkus.jimmer.client.openapi.properties.info.title", "Partial API"))) {
                var config = config(values);
                var generator = new OpenApiDocumentGenerator(config.client().openapi().properties(), 500);
                String document = new String(generator.generate(Metadatas.create(false, "public", null)),
                        StandardCharsets.UTF_8);

                assertTrue(document.contains(values.isEmpty() ? "<No title>" : "Partial API"), document);
                assertTrue(document.contains("<No Description>"), document);
                assertTrue(document.contains("version: 1.0.0"), document);
                assertFalse(document.contains("contact:"), document);
                assertFalse(document.contains("license:"), document);
                assertFalse(document.contains("servers:"), document);
                assertFalse(document.contains("securitySchemes:"), document);
                assertFalse(document.contains("flows:"), document);
            }
        });
    }

    @Test
    void preservesExplicitInfoAndOAuthFlowProperties() throws IOException {
        withMetadata(() -> {
            var config = config(Map.ofEntries(
                    Map.entry("quarkus.jimmer.client.openapi.properties.info.title", "Explicit API"),
                    Map.entry("quarkus.jimmer.client.openapi.properties.info.description", "An explicit description"),
                    Map.entry("quarkus.jimmer.client.openapi.properties.info.version", "2.1.0"),
                    Map.entry("quarkus.jimmer.client.openapi.properties.info.contact.email", "api@example.org"),
                    Map.entry("quarkus.jimmer.client.openapi.properties.info.license.name", "Apache"),
                    Map.entry("quarkus.jimmer.client.openapi.properties.info.license.identifier", "Apache-2.0"),
                    Map.entry("quarkus.jimmer.client.openapi.properties.components.securitySchemes.oauth.type", "oauth2"),
                    Map.entry(
                            "quarkus.jimmer.client.openapi.properties.components.securitySchemes.oauth.flows.authorizationCode.authorizationUrl",
                            "https://example.org/authorize"),
                    Map.entry(
                            "quarkus.jimmer.client.openapi.properties.components.securitySchemes.oauth.flows.authorizationCode.tokenUrl",
                            "https://example.org/token"),
                    Map.entry(
                            "quarkus.jimmer.client.openapi.properties.components.securitySchemes.oauth.flows.authorizationCode.scopes.read",
                            "Read API")));
            var generator = new OpenApiDocumentGenerator(config.client().openapi().properties(), 500);
            String document = new String(generator.generate(Metadatas.create(false, "public", null)), StandardCharsets.UTF_8);

            for (String expected : List.of("Explicit API", "An explicit description", "2.1.0", "contact:", "api@example.org",
                    "license:", "Apache-2.0", "flows:", "authorizationCode:", "https://example.org/authorize",
                    "https://example.org/token", "Read API")) {
                assertTrue(document.contains(expected), document);
            }
            assertFalse(document.contains("<No title>"), document);
            assertFalse(document.contains("<No Description>"), document);
            assertFalse(document.contains("implicit:"), document);
            assertFalse(document.contains("password:"), document);
            assertFalse(document.contains("clientCredentials:"), document);
        });
    }

    @Test
    void reusableGeneratorDoesNotShareRenderedSchemasAcrossDocumentsOrThreads() throws IOException {
        withMetadata(() -> {
            var generator = new OpenApiDocumentGenerator(config().client().openapi().properties(), 422);
            var publicMetadata = Metadatas.create(false, "public", "/public-prefix");
            var adminMetadata = Metadatas.create(false, "admin", "/admin-prefix");
            String publicDocument = new String(generator.generate(publicMetadata), StandardCharsets.UTF_8);
            String adminDocument = new String(generator.generate(adminMetadata), StandardCharsets.UTF_8);
            assertTrue(publicDocument.contains("EXPECTED_FAILURE"), publicDocument);
            assertFalse(adminDocument.contains("EXPECTED_FAILURE"), adminDocument);
            assertFalse(publicDocument.contains("/admin-prefix/"), publicDocument);
            assertFalse(adminDocument.contains("/public-prefix/"), adminDocument);

            var executor = Executors.newFixedThreadPool(4);
            try {
                List<CompletableFuture<String>> results = new ArrayList<>();
                for (int index = 0; index < 24; index++) {
                    var metadata = index % 2 == 0 ? publicMetadata : adminMetadata;
                    results.add(CompletableFuture.supplyAsync(
                            () -> new String(generator.generate(metadata), StandardCharsets.UTF_8), executor));
                }
                for (int index = 0; index < results.size(); index++) {
                    assertEquals(index % 2 == 0 ? publicDocument : adminDocument, results.get(index).join());
                }
            } finally {
                executor.shutdownNow();
            }
        });
    }

    @Test
    void snapshotsConfigurationAndNestedSecurityRequirementsAtConstruction() throws IOException {
        withMetadata(() -> {
            var original = config(Map.of(
                    "quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.type", "apiKey",
                    "quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.name", "token"))
                    .client().openapi().properties();
            List<String> scopes = new ArrayList<>(List.of("originalScope"));
            Map<String, List<String>> requirement = new LinkedHashMap<>(Map.of("token", scopes));
            List<Map<String, List<String>>> requirements = new ArrayList<>(List.of(requirement));
            AtomicBoolean constructed = new AtomicBoolean();
            JimmerOpenApiConfig.Properties properties = (JimmerOpenApiConfig.Properties) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] { JimmerOpenApiConfig.Properties.class },
                    (proxy, method, arguments) -> {
                        assertFalse(constructed.get(), "Configuration must only be read while creating the generator");
                        return method.getName().equals("securities") ? Optional.of(requirements)
                                : method.invoke(original, arguments);
                    });
            var generator = new OpenApiDocumentGenerator(properties, 500);
            constructed.set(true);
            scopes.clear();
            scopes.add("changedScope");
            requirement.put("changedScheme", List.of("anotherScope"));
            requirements.clear();

            for (int index = 0; index < 2; index++) {
                String document = new String(generator.generate(Metadatas.create(false, "public", null)),
                        StandardCharsets.UTF_8);
                assertTrue(document.contains("originalScope"), document);
                assertFalse(document.contains("changedScope"), document);
                assertFalse(document.contains("changedScheme"), document);
            }
        });
    }

    @Test
    void preservesConfiguredApiKeyLocations() throws IOException {
        withMetadata(() -> {
            for (String location : new String[] { "query", "header", "cookie" }) {
                JimmerBuildTimeConfig config = config(Map.of(
                        "quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.type", "apiKey",
                        "quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.name", "token",
                        "quarkus.jimmer.client.openapi.properties.components.securitySchemes.token.in", location));
                String document = new String(
                        OpenApiDocumentGenerator.generate(Metadatas.create(false, "public", null), config),
                        StandardCharsets.UTF_8);
                assertTrue(document.contains("in: " + location), document);
            }
        });
    }

    @Test
    void generatesTypeScriptWithoutAnHttpRequestAndPreservesClientOptions() throws IOException {
        withMetadata(() -> {
            var metadata = Metadatas.create(true, "public", "/v1");
            String defaults = String.join("\n",
                    sources(TypeScriptGenerator.generate(metadata, config().client().ts())).values());
            assertTrue(defaults.contains("readonly name?: string | undefined;"));
            assertFalse(defaults.contains("export enum "));

            JimmerBuildTimeConfig config = config(Map.of(
                    "quarkus.jimmer.client.ts.api-name", "BookApi",
                    "quarkus.jimmer.client.ts.indent", "2",
                    "quarkus.jimmer.client.ts.mutable", "true",
                    "quarkus.jimmer.client.ts.null-render-mode", "NULL_OR_UNDEFINED",
                    "quarkus.jimmer.client.ts.is-enum-ts-style", "true"));
            Map<String, String> archive = sources(TypeScriptGenerator.generate(metadata, config.client().ts()));
            String customized = String.join("\n", archive.values());
            assertTrue(archive.containsKey("BookApi.ts"));
            assertTrue(customized.contains("\n  name?: string | null | undefined;"));
            assertFalse(customized.contains("readonly name?"));
            assertTrue(customized.contains("export enum "));
            assertTrue(customized.contains("_CONSTANT_MAP"));
            assertTrue(customized.contains("/v1/resource/read"));
            assertFalse(customized.contains("/admin/read"));
        });
    }

    @Test
    void handlersPreserveDynamicGroups() throws IOException {
        withMetadata(() -> {
            var original = config();
            AtomicBoolean constructed = new AtomicBoolean();
            JimmerBuildTimeConfig setupOnly = (JimmerBuildTimeConfig) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { JimmerBuildTimeConfig.class }, (proxy, method, arguments) -> {
                        assertFalse(constructed.get(), "HTTP requests must not access the fixed build-time configuration");
                        return method.invoke(original, arguments);
                    });
            var openApi = new OpenApiRecorder().getHandler(setupOnly);
            constructed.set(true);
            var typeScript = new TypeScriptRecorder().getHandler(config());
            for (String group : new String[] { "public", "admin" }) {
                HttpTestResponse yaml = new HttpTestResponse();
                openApi.handle(yaml.context(Map.of("groups", group)));
                HttpTestResponse zip = new HttpTestResponse();
                typeScript.handle(zip.context(Map.of("groups", group)));
                String included = group.equals("public") ? "/resource/read" : "/admin/read";
                String excluded = group.equals("public") ? "/admin/read" : "/resource/read";
                assertTrue(yaml.body.toString().contains(included));
                assertFalse(yaml.body.toString().contains(excluded));
                String sources = String.join("\n", sources(zip.body.getBytes()).values());
                assertTrue(sources.contains(included));
                assertFalse(sources.contains(excluded));
            }
        });
    }

    private JimmerBuildTimeConfig config() {
        return config(Map.of("quarkus.jimmer.client.openapi.properties.info.title", "Recorded HTTP API"));
    }

    private JimmerBuildTimeConfig config(Map<String, String> properties) {
        return new SmallRyeConfigBuilder().addDefaultInterceptors().withMapping(JimmerBuildTimeConfig.class)
                .withDefaultValues(properties)
                .build().getConfigMapping(JimmerBuildTimeConfig.class);
    }

    private static Map<String, String> sources(byte[] archive) {
        Map<String, String> sources = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (!entry.isDirectory()) {
                    sources.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new AssertionError("Invalid TypeScript archive", e);
        }
        return sources;
    }

    private void withMetadata(Runnable action) throws IOException {
        Path schema = temporaryDirectory.resolve("client.json");
        Files.writeString(schema, """
                {
                  "services": [
                    {
                      "typeName": "%s", "groups": ["public"],
                      "operations": [{"name": "read", "key": "read", "parameters": [],
                        "returnType": {"typeName": "%s"}, "exceptions": ["%s"]}]
                    },
                    {
                      "typeName": "%s", "groups": ["admin"],
                      "operations": [{"name": "read", "key": "read", "parameters": [],
                        "returnType": {"typeName": "java.lang.String"}}]
                    }
                  ],
                  "definitions": [
                    {"typeName": "%s", "kind": "OBJECT", "props": [
                      {"name": "name", "type": {"typeName": "java.lang.String", "nullable": true}},
                      {"name": "state", "type": {"typeName": "%s"}}
                    ]},
                    {"typeName": "%s", "kind": "ENUM", "constants": [{"name": "AVAILABLE"}]},
                    {"typeName": "%s", "kind": "OBJECT", "props": [],
                      "error": {"family": "HTTP_TEST", "code": "EXPECTED_FAILURE"}}
                  ]
                }
                """.formatted(TypeName.of(ResourceApi.class).toString(true), TypeName.of(Payload.class).toString(true),
                TypeName.of(HttpFeatureTestSupport.Failure.class).toString(true), TypeName.of(AdminApi.class).toString(true),
                TypeName.of(Payload.class).toString(true), TypeName.of(State.class).toString(true),
                TypeName.of(State.class).toString(true), TypeName.of(HttpFeatureTestSupport.Failure.class).toString(true)));
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = new ClassLoader(original) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals(Constant.CLIENT_RESOURCE)) {
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
            action.run();
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @jakarta.ws.rs.Path("/resource")
    public interface ResourceApi {
        @jakarta.ws.rs.GET
        @jakarta.ws.rs.Path("/read")
        Payload read();
    }

    @jakarta.ws.rs.Path("/admin")
    public interface AdminApi {
        @jakarta.ws.rs.GET
        @jakarta.ws.rs.Path("/read")
        String read();
    }

    public record Payload(String name, State state) {
    }

    public enum State {
        AVAILABLE
    }
}
