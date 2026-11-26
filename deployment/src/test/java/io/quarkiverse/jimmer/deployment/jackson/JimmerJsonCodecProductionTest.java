package io.quarkiverse.jimmer.deployment.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.jackson.codec.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.test.QuarkusProdModeTest;

class JimmerJsonCodecProductionTest {

    @RegisterExtension
    static final QuarkusProdModeTest APP = new QuarkusProdModeTest()
            .withApplicationRoot(archive -> archive.addClasses(CodecProbe.class, NativeValue.class, NativeValueDraft.class))
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .setRuntimeProperties(Map.of("quarkus.http.host", "127.0.0.1", "quarkus.http.port", "0"))
            .setRun(true)
            .setExpectExit(true);

    @Test
    void usesOnlyJackson3InThePackagedApplication() {
        String output = APP.getStartupConsoleOutput();
        assertEquals(0, APP.getExitCode(), output);
        assertTrue(output.contains("V3 decoded packaged"), output);
    }

    @QuarkusMain
    public static class CodecProbe implements QuarkusApplication {
        @Override
        public int run(String... args) {
            var loader = Thread.currentThread().getContextClassLoader();
            for (String resource : new String[] { "com/fasterxml/jackson/core/JsonFactory.class",
                    "com/fasterxml/jackson/databind/ObjectMapper.class" }) {
                if (loader.getResource(resource) != null) {
                    throw new IllegalStateException("Unexpected Jackson 2 runtime class: " + resource);
                }
            }
            NativeValue value = NativeValueDraft.$.produce(draft -> draft.setText("packaged"));
            String json = ImmutableObjects.toString(value);
            NativeValue decoded = ImmutableObjects.fromString(NativeValue.class, json);
            System.out.println(JsonCodec.jsonCodec().version() + " decoded " + decoded.text());
            return 0;
        }
    }
}
