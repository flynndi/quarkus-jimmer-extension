package io.quarkiverse.jimmer.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.JoinTable;
import org.babyfish.jimmer.sql.LogicalDeleted;
import org.babyfish.jimmer.sql.meta.LogicalDeletedLongGenerator;
import org.babyfish.jimmer.sql.meta.LogicalDeletedUUIDGenerator;
import org.babyfish.jimmer.sql.meta.LogicalDeletedValueGenerator;
import org.babyfish.jimmer.sql.meta.UserIdGenerator;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.classloading.ClassPathElement;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassConditionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;

class JimmerProcessorTest {

    @TempDir
    Path optionalClasses;

    @Test
    void optionalDialectReflectionUsesRuntimeResourcesRatherThanDeploymentResources() throws Exception {
        var optional = Map.of(
                "org.babyfish.jimmer.sql.dialect.PostgresDialect", "org.postgresql.util.PGobject",
                "org.babyfish.jimmer.sql.dialect.H2Dialect", "org.h2.value.ValueJson");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        for (var entry : optional.entrySet()) {
            Path directory = optionalClasses.resolve(entry.getKey());
            Path marker = directory.resolve(entry.getValue().replace('.', '/') + ".class");
            Files.createDirectories(marker.getParent());
            // Presence detection reads resources, not driver classes or their static initializers.
            Files.write(marker, new byte[0]);
            for (boolean runtime : List.of(false, true)) {
                List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
                List<ReflectiveClassConditionBuildItem> conditions = new ArrayList<>();
                try (QuarkusClassLoader loader = QuarkusClassLoader.builder("Optional dialect dependencies", previous, false)
                        .addNormalPriorityElement(ClassPathElement.fromPath(directory, runtime)).build()) {
                    Thread.currentThread().setContextClassLoader(loader);
                    new JimmerProcessor().registerDialectsForReflection(reflection::add, conditions::add);
                } finally {
                    Thread.currentThread().setContextClassLoader(previous);
                }
                var guards = conditions.stream().collect(Collectors.toMap(
                        ReflectiveClassConditionBuildItem::getClassName, ReflectiveClassConditionBuildItem::getTypeReachable));
                var expected = new java.util.HashMap<>(optional);
                if (runtime) {
                    expected.remove(entry.getKey());
                }
                assertEquals(expected, guards);
                assertEquals(runtime ? 9 : 8, reflection.size());
                assertEquals(runtime, reflection.stream().anyMatch(item -> item.getClassNames().contains(entry.getKey())));
                assertTrue(reflection.stream().anyMatch(item -> item.getClassNames()
                        .contains("org.babyfish.jimmer.sql.dialect.MySqlDialect")));
                for (var item : reflection) {
                    assertTrue(item.isConstructors());
                    assertFalse(item.isMethods());
                    assertFalse(item.isFields());
                    assertFalse(item.isClasses());
                }
            }
        }
    }

    @Test
    void registersDefaultAndExplicitGeneratorsWithOnlyPublicNoArgConstructors() throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : List.of(GeneratorProperties.class, IdGenerator.class, DeleteGenerator.class,
                JoinTableDeleteGenerator.class, LogicalDeletedLongGenerator.class, LogicalDeletedUUIDGenerator.class,
                InjectedGenerator.class, UserIdGenerator.None.class, LogicalDeletedValueGenerator.None.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        var applicationIndexer = new Indexer();
        applicationIndexer.indexClass(GeneratorProperties.class);
        List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
        // Referenced generator classes can be resolved through the computing index without joining the application index.
        new JimmerProcessor().registerGeneratorConstructors(
                new CombinedIndexBuildItem(applicationIndexer.complete(), index), constructors::add);
        assertEquals(Set.of(IdGenerator.class.getName(), DeleteGenerator.class.getName(),
                JoinTableDeleteGenerator.class.getName(), LogicalDeletedLongGenerator.class.getName(),
                LogicalDeletedUUIDGenerator.class.getName()),
                constructors.stream().map(ReflectiveMethodBuildItem::getDeclaringClass).collect(Collectors.toSet()));
        assertEquals(5, constructors.size(), "Repeated model references must not duplicate registrations");
        for (var constructor : constructors) {
            assertEquals("<init>", constructor.getName());
            assertEquals(0, constructor.getParams().length);
            assertFalse(constructor.isQueryOnly(), "StrategyProvider invokes the constructor");
        }
    }

    private interface GeneratorProperties {
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        long identity();

        @GeneratedValue(generatorType = UserIdGenerator.None.class)
        long defaultGenerator();

        @GeneratedValue(generatorRef = "namedGenerator")
        long namedGenerator();

        @GeneratedValue(generatorType = IdGenerator.class)
        long generatedId();

        @GeneratedValue(generatorType = IdGenerator.class)
        long anotherGeneratedId();

        @GeneratedValue(generatorType = InjectedGenerator.class)
        long injectedId();

        @LogicalDeleted("true")
        boolean deleted();

        @LogicalDeleted(generatorType = LogicalDeletedValueGenerator.None.class)
        long defaultDeletedGenerator();

        @LogicalDeleted(generatorType = DeleteGenerator.class)
        long deletedValue();

        @LogicalDeleted
        long deletedTimestamp();

        @LogicalDeleted
        UUID deletedUuid();

        @JoinTable(logicalDeletedFilter = @JoinTable.LogicalDeletedFilter(columnName = "DELETED", type = long.class, generatorType = JoinTableDeleteGenerator.class))
        List<Long> deletedAssociations();

        @JoinTable
        List<Long> associations();
    }

    public static class IdGenerator implements UserIdGenerator<Long> {
        @Override
        public Long generate(Class<?> entityType) {
            return 1L;
        }
    }

    public static class DeleteGenerator implements LogicalDeletedValueGenerator<Long> {
        @Override
        public Long generate() {
            return 1L;
        }
    }

    public static class JoinTableDeleteGenerator implements LogicalDeletedValueGenerator<Long> {
        @Override
        public Long generate() {
            return 1L;
        }
    }

    public static class InjectedGenerator implements UserIdGenerator<Long> {
        @Inject
        public InjectedGenerator(String dependency) {
        }

        @Override
        public Long generate(Class<?> entityType) {
            return 1L;
        }
    }
}
