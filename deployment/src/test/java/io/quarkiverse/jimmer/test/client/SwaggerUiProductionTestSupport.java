package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

final class SwaggerUiProductionTestSupport {

    static final String UI_PATH = "/native/swagger";
    static final String SPEC_PATH = "/jimmer/openapi.yml";

    private SwaggerUiProductionTestSupport() {
    }

    static QuarkusProdModeTest application(String name) {
        return new QuarkusProdModeTest()
                .withApplicationRoot(HttpFeatureTestSupport::addMetadata)
                .setApplicationName(name)
                .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-swagger-ui", Version.getVersion())))
                .overrideConfigKey("quarkus.devservices.enabled", "false")
                .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
                .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
                .overrideConfigKey("quarkus.datasource.jdbc", "false")
                .overrideConfigKey("quarkus.jimmer.transaction-cache-operator-fixed-delay", "off")
                .overrideConfigKey("quarkus.jimmer.client.openapi.path", SPEC_PATH)
                .overrideConfigKey("quarkus.swagger-ui.path", UI_PATH)
                .overrideConfigKey("quarkus.swagger-ui.urls.jimmer", SPEC_PATH)
                .setRuntimeProperties(Map.of("quarkus.http.host", "127.0.0.1", "quarkus.http.port", "0"))
                .setRun(true);
    }

    static URI baseUri(QuarkusProdModeTest application) {
        String output = application.getStartupConsoleOutput();
        var listening = Pattern.compile("Listening on: (http://127\\.0\\.0\\.1:[0-9]+)").matcher(output);
        assertTrue(listening.find(), output);
        return URI.create(listening.group(1));
    }

    static boolean hasPackagedAsset(ProdModeTestResults results, String fileName) throws IOException {
        try (var files = Files.walk(results.getBuiltArtifactPath().getParent())) {
            for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).toList()) {
                try (ZipFile archive = new ZipFile(jar.toFile())) {
                    if (archive.stream().anyMatch(entry -> entry.getName().endsWith("/" + fileName))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static Ports availablePorts() {
        try (ServerSocket http = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                ServerSocket management = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return new Ports(http.getLocalPort(), management.getLocalPort());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static void preserveIfRequested(ProdModeTestResults results, Map<String, String> metadata) throws IOException {
        if (!Boolean.getBoolean("jimmer.test.keep-swagger-ui-app")) {
            return;
        }
        // Quarkus 3.39's test harness has no public cleanup opt-out. Copy the complete fast-jar directory instead.
        Path source = results.getBuiltArtifactPath().getParent();
        Path destination = Files.createTempDirectory("jimmer-swagger-ui-production-");
        try (var files = Files.walk(source)) {
            for (Path file : files.toList()) {
                Path target = destination.resolve(source.relativize(file));
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
        Properties properties = new Properties();
        properties.putAll(metadata);
        properties.setProperty("launch.command", "java -jar "
                + destination.resolve(results.getBuiltArtifactPath().getFileName()));
        try (var writer = Files.newBufferedWriter(destination.resolve("browser-review.properties"))) {
            properties.store(writer, "Production Swagger UI browser review");
        }
        System.out.println("Retained Swagger UI production application: "
                + destination.resolve(results.getBuiltArtifactPath().getFileName()));
        System.out.println("Browser review URLs: " + metadata);
    }

    record Ports(int http, int management) {
        String httpOrigin() {
            return "http://127.0.0.1:" + http;
        }

        String managementOrigin() {
            return "http://127.0.0.1:" + management;
        }
    }
}
