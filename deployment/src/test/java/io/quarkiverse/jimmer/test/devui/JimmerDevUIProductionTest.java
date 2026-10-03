package io.quarkiverse.jimmer.test.devui;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.devui.JimmerDevUIService;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusProdModeTest;

class JimmerDevUIProductionTest {
    @RegisterExtension
    static final QuarkusProdModeTest APP = new QuarkusProdModeTest()
            .withApplicationRoot(archive -> archive.addClass(DiagnosticsProbe.class))
            .setApplicationName("jimmer-devui-production")
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.jdbc", "false")
            .setRuntimeProperties(Map.of("quarkus.http.host", "127.0.0.1", "quarkus.http.port", "0"))
            .setRun(true);

    @Test
    void doesNotRegisterTheProviderOrServeDevUiInProduction() throws Exception {
        String startup = APP.getStartupConsoleOutput();
        var listening = Pattern.compile("Listening on: (http://127\\.0\\.0\\.1:[0-9]+)").matcher(startup);
        assertTrue(listening.find(), startup);
        URI uri = URI.create(listening.group(1));
        HttpClient http = HttpClient.newHttpClient();
        var probe = http.send(HttpRequest.newBuilder(uri.resolve("/diagnostics-probe"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, probe.statusCode(), probe.body());
        assertEquals("absent", probe.body());
        for (String path : new String[] { "/q/dev-ui/quarkus-jimmer/runtime", "/q/dev-ui/json-rpc-ws" }) {
            var response = http.send(HttpRequest.newBuilder(uri.resolve(path)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode(), path);
        }
    }

    @Path("/diagnostics-probe")
    public static class DiagnosticsProbe {
        @GET
        @Produces("text/plain")
        public String provider() {
            return Arc.container().select(JimmerDevUIService.class).isUnsatisfied() ? "absent" : "present";
        }
    }
}
