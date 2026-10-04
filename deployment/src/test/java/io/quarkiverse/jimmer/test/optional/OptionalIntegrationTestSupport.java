package io.quarkiverse.jimmer.test.optional;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkus.bootstrap.classloading.ClassPathElement;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.test.QuarkusUnitTest;

final class OptionalIntegrationTestSupport {

    private OptionalIntegrationTestSupport() {
    }

    static QuarkusUnitTest isolateExcludedDependencies(QuarkusUnitTest test, Set<ArtifactKey> excluded) {
        AtomicReference<QuarkusClassLoader> isolation = new AtomicReference<>();
        return test.setExcludedDependencies(excluded)
                .addBootstrapCustomizer(builder -> {
                    // Dependency exclusion updates the application model, but Quarkus 3.39's ArC ValueRegistry
                    // discovery can still see excluded providers through the Maven test parent classloader.
                    // Preserve the test framework's existing parent (and its test-class bans).
                    ClassLoader parent = builder.build().getBaseClassLoader();
                    var filtered = QuarkusClassLoader.builder("Excluded optional dependencies", parent, false);
                    for (Path path : parentArtifactPaths(parent, excluded)) {
                        filtered.addBannedElement(ClassPathElement.fromPath(path, false));
                    }
                    QuarkusClassLoader loader = filtered.build();
                    isolation.set(loader);
                    builder.setBaseClassLoader(loader);
                    // A shared service resource is only banned in this parent layer. Augmentation still discovers
                    // service descriptors from every included application dependency in its own classpath.
                })
                .setAfterAllCustomizer(() -> {
                    QuarkusClassLoader loader = isolation.getAndSet(null);
                    if (loader != null) {
                        loader.close();
                    }
                });
    }

    private static Set<Path> parentArtifactPaths(ClassLoader parent, Set<ArtifactKey> artifacts) {
        Set<Path> paths = new LinkedHashSet<>();
        try {
            for (ArtifactKey artifact : artifacts) {
                String metadata = "META-INF/maven/" + artifact.getGroupId() + "/" + artifact.getArtifactId()
                        + "/pom.properties";
                var resources = parent.getResources(metadata);
                while (resources.hasMoreElements()) {
                    URL resource = resources.nextElement();
                    if (resource.openConnection() instanceof JarURLConnection jar) {
                        paths.add(Path.of(jar.getJarFileURL().toURI()));
                    } else if ("file".equals(resource.getProtocol())) {
                        Path root = Path.of(resource.toURI());
                        for (int i = 0; i < Path.of(metadata).getNameCount(); i++) {
                            root = root.getParent();
                        }
                        paths.add(root);
                    } else {
                        throw new IllegalStateException("Unsupported test dependency resource: " + resource);
                    }
                }
            }
        } catch (IOException | URISyntaxException ex) {
            throw new IllegalStateException("Cannot isolate excluded test dependencies", ex);
        }
        return paths;
    }

    static Set<ArtifactKey> withoutHttp() {
        return Stream.concat(withoutRestClient().stream(), Stream.of(
                "quarkus-vertx-http", "quarkus-vertx-http-deployment",
                "quarkus-rest", "quarkus-rest-deployment",
                "quarkus-rest-jackson", "quarkus-rest-jackson-deployment")
                .map(name -> ArtifactKey.of("io.quarkus", name)))
                .collect(Collectors.toSet());
    }

    static Set<ArtifactKey> withoutRestClient() {
        return Stream.of("quarkus-rest-client", "quarkus-rest-client-deployment",
                "quarkus-rest-client-jackson", "quarkus-rest-client-jackson-deployment")
                .map(name -> ArtifactKey.of("io.quarkus", name))
                .collect(Collectors.toSet());
    }

    static Set<ArtifactKey> withoutScheduler() {
        return Stream.of("quarkus-scheduler", "quarkus-scheduler-deployment", "quarkus-scheduler-api",
                "quarkus-scheduler-common", "quarkus-scheduler-kotlin", "quarkus-scheduler-dev",
                "quarkus-quartz", "quarkus-quartz-deployment")
                .map(name -> ArtifactKey.of("io.quarkus", name))
                .collect(Collectors.toSet());
    }

    static Set<ArtifactKey> withoutCacheBackends() {
        Set<ArtifactKey> artifacts = Stream.of("quarkus-redis-client", "quarkus-redis-client-deployment",
                "quarkus-caffeine", "quarkus-caffeine-deployment")
                .map(name -> ArtifactKey.of("io.quarkus", name))
                .collect(Collectors.toSet());
        artifacts.add(ArtifactKey.of("com.github.ben-manes.caffeine", "caffeine"));
        return artifacts;
    }

    static String messages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
