package io.quarkiverse.jimmer.deployment.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.jackson.codec.JacksonVersion;
import org.babyfish.jimmer.jackson.codec.JsonCodec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.maven.dependency.Dependency;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.test.QuarkusProdModeTest;

class JimmerJsonCodecProductionTest {

    private static QuarkusProdModeTest application() {
        return new QuarkusProdModeTest()
                .withApplicationRoot(archive -> archive
                        .addClasses(CodecProbe.class, NativeValue.class, NativeValueDraft.class))
                .overrideConfigKey("quarkus.devservices.enabled", "false")
                .overrideConfigKey("quarkus.datasource.jdbc", "false")
                .setRuntimeProperties(Map.of("quarkus.http.host", "127.0.0.1", "quarkus.http.port", "0"))
                .setRun(true)
                .setExpectExit(true);
    }

    @Nested
    class Jackson2Only {
        @RegisterExtension
        static final QuarkusProdModeTest APP = application();

        @Test
        void usesTheJackson2Codec() {
            assertRoundTrip(APP, JacksonVersion.V2);
        }
    }

    @Nested
    class Jackson2And3 {
        @RegisterExtension
        static final QuarkusProdModeTest APP = application()
                .setForcedDependencies(List.of(Dependency.of("tools.jackson.core", "jackson-databind", "3.1.6")));

        @Test
        void keepsTheJackson3CodecForJimmersDefaultProvider() {
            assertRoundTrip(APP, JacksonVersion.V3);
        }
    }

    private static void assertRoundTrip(QuarkusProdModeTest app, JacksonVersion version) {
        String output = app.getStartupConsoleOutput();
        assertEquals(0, app.getExitCode(), output);
        assertTrue(output.contains(version + " decoded packaged"), output);
    }

    @QuarkusMain
    public static class CodecProbe implements QuarkusApplication {
        @Override
        public int run(String... args) {
            NativeValue value = NativeValueDraft.$.produce(draft -> draft.setText("packaged"));
            String json = ImmutableObjects.toString(value);
            NativeValue decoded = ImmutableObjects.fromString(NativeValue.class, json);
            System.out.println(JsonCodec.jsonCodec().version() + " decoded " + decoded.text());
            return 0;
        }
    }
}
