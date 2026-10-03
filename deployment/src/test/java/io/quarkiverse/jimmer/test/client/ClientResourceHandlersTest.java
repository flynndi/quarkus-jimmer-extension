package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.babyfish.jimmer.client.meta.TypeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.openapi.OpenApiRecorder;
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

    private JimmerBuildTimeConfig config() {
        return new SmallRyeConfigBuilder().addDefaultInterceptors().withMapping(JimmerBuildTimeConfig.class)
                .withDefaultValue("quarkus.jimmer.client.openapi.properties.info.title", "Recorded HTTP API")
                .build().getConfigMapping(JimmerBuildTimeConfig.class);
    }

    private void withMetadata(Runnable action) throws IOException {
        Path schema = temporaryDirectory.resolve("client.json");
        Files.writeString(schema, "{\"services\":[{\"typeName\":\"" + TypeName.of(ResourceApi.class).toString(true)
                + "\",\"operations\":[{\"name\":\"read\",\"key\":\"read\",\"parameters\":[],\"returnType\":{\"typeName\":\"java.lang.String\"}}]}],\"definitions\":[]}");
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
        String read();
    }
}
