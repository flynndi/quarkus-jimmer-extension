package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.babyfish.jimmer.client.meta.TypeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.openapi.CssRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.JsRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiRecorder;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiUiRecorder;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptRecorder;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.smallrye.config.SmallRyeConfigBuilder;

class ClientResourceHandlersTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersTheCompleteUtf8TemplateUsingRecordedConfigWithoutCdi() throws IOException {
        String template = "<html>" + "中文页面".repeat(600)
                + "${openapi.css}|${openapi.js}|${openapi.refPath}</html>";
        withResources(true, Map.of(Constant.TEMPLATE_RESOURCE, template.getBytes(StandardCharsets.UTF_8)), () -> {
            HttpTestResponse response = new HttpTestResponse();
            new OpenApiUiRecorder().getHandler(config()).handle(response.context(Map.of("groups", "a b")));
            // The pinned Swagger artifact contains the template, but not bundled CSS/JS assets.
            String expected = template.replace("${openapi.css}", "https://unpkg.com/swagger-ui-dist@5.10.5/swagger-ui.css")
                    .replace("${openapi.js}", "https://unpkg.com/swagger-ui-dist@5.10.5/swagger-ui-bundle.js")
                    .replace("${openapi.refPath}", "/custom/spec?groups=a+b");
            assertEquals(expected, response.body.toString(StandardCharsets.UTF_8));
            assertEquals(Constant.TEXT_HTML, response.headers.get("content-type"));
        });
    }

    @Test
    void rendersTheCompleteNoMetadataPage() throws IOException {
        byte[] page = ("<html>" + "no-metadata ".repeat(200) + "</html>").getBytes(StandardCharsets.UTF_8);
        withResources(false, Map.of(Constant.NO_METADATA_RESOURCE, page), () -> {
            HttpTestResponse response = new HttpTestResponse();
            JimmerBuildTimeConfig localSpec = new SmallRyeConfigBuilder().addDefaultInterceptors()
                    .withMapping(JimmerBuildTimeConfig.class)
                    .withDefaultValue("quarkus.jimmer.client.openapi.path", "/local-spec")
                    .build().getConfigMapping(JimmerBuildTimeConfig.class);
            new OpenApiUiRecorder().getHandler(localSpec).handle(response.context(Map.of()));
            assertArrayEquals(page, response.body.getBytes());
        });
    }

    @Test
    void externalSpecificationDoesNotRequireLocalMetadataAndPreservesItsQueryAndFragment() throws IOException {
        withResources(false, Map.of(Constant.TEMPLATE_RESOURCE, "${openapi.refPath}".getBytes(StandardCharsets.UTF_8)), () -> {
            JimmerBuildTimeConfig external = new SmallRyeConfigBuilder().addDefaultInterceptors()
                    .withMapping(JimmerBuildTimeConfig.class)
                    .withDefaultValue("quarkus.jimmer.client.openapi.ref-path", "https://example.org/spec?version=1#api")
                    .build().getConfigMapping(JimmerBuildTimeConfig.class);
            HttpTestResponse response = new HttpTestResponse();
            new OpenApiUiRecorder().getHandler(external).handle(response.context(Map.of("groups", "a b")));
            assertEquals("https://example.org/spec?version=1&groups=a+b#api", response.body.toString());
        });
    }

    @Test
    void staticAssetsDoNotRequireAnArcContainer() throws IOException {
        byte[] css = "body { color: red; }".repeat(600).getBytes(StandardCharsets.UTF_8);
        byte[] js = "console.log('asset');".repeat(600).getBytes(StandardCharsets.UTF_8);
        withResources(false, Map.of(Constant.CSS_RESOURCE, css, Constant.JS_RESOURCE, js), () -> {
            HttpTestResponse cssResponse = new HttpTestResponse();
            new CssRecorder().getHandler().handle(cssResponse.context(Map.of()));
            assertArrayEquals(css, cssResponse.body.getBytes());
            assertEquals(Constant.TEXT_CSS, cssResponse.headers.get("content-type"));
            HttpTestResponse jsResponse = new HttpTestResponse();
            new JsRecorder().getHandler().handle(jsResponse.context(Map.of()));
            assertArrayEquals(js, jsResponse.body.getBytes());
            assertEquals(Constant.TEXT_JAVASCRIPT, jsResponse.headers.get("content-type"));
        });
    }

    @Test
    void generatedDocumentsUseExplicitConfigWithoutLookingUpCdi() throws IOException {
        withResources(true, Map.of(), () -> {
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

    private JimmerBuildTimeConfig config() {
        return new SmallRyeConfigBuilder().addDefaultInterceptors().withMapping(JimmerBuildTimeConfig.class)
                .withDefaultValue("quarkus.jimmer.client.openapi.ref-path", "/custom/spec")
                .withDefaultValue("quarkus.jimmer.client.openapi.properties.info.title", "Recorded HTTP API")
                .build().getConfigMapping(JimmerBuildTimeConfig.class);
    }

    private void withResources(boolean metadata, Map<String, byte[]> resources, Runnable action) throws IOException {
        Path schema = temporaryDirectory.resolve("client.json");
        Files.writeString(schema, metadata
                ? "{\"services\":[{\"typeName\":\"" + TypeName.of(ResourceApi.class).toString(true)
                        + "\",\"operations\":[{\"name\":\"read\",\"key\":\"read\",\"parameters\":[],\"returnType\":{\"typeName\":\"java.lang.String\"}}]}],\"definitions\":[]}"
                : "{\"services\":[],\"definitions\":[]}");
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

            @Override
            public InputStream getResourceAsStream(String name) {
                byte[] bytes = resources.get(name);
                return bytes != null ? new ByteArrayInputStream(bytes) : super.getResourceAsStream(name);
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
        String read();
    }
}
