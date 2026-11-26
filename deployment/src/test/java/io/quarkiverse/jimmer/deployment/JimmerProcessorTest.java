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
import org.babyfish.jimmer.sql.ast.Predicate;
import org.babyfish.jimmer.sql.ast.table.WeakJoin;
import org.babyfish.jimmer.sql.ast.table.spi.UsingWeakJoinMetadataParser;
import org.babyfish.jimmer.sql.ast.table.spi.WeakJoinMetadata;
import org.babyfish.jimmer.sql.ast.table.spi.WeakJoinMetadataParser;
import org.babyfish.jimmer.sql.dialect.DefaultDialect;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.meta.LogicalDeletedLongGenerator;
import org.babyfish.jimmer.sql.meta.LogicalDeletedUUIDGenerator;
import org.babyfish.jimmer.sql.meta.LogicalDeletedValueGenerator;
import org.babyfish.jimmer.sql.meta.UserIdGenerator;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookTable;
import io.quarkiverse.jimmer.test.model.CdiBookTableEx;
import io.quarkus.bootstrap.classloading.ClassPathElement;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassConditionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;

class JimmerProcessorTest {

    @TempDir
    Path optionalClasses;

    @Test
    void registersGeneratedTablesFromDependenciesWithoutRegisteringAbsentTables() throws IOException {
        Indexer entities = new Indexer();
        entities.indexClass(CdiBook.class);
        var applicationIndex = entities.complete();
        Indexer tables = new Indexer();
        tables.indexClass(CdiBookTable.class);
        tables.indexClass(CdiBookTableEx.class);
        var dependencyIndex = tables.complete();
        for (var computingIndex : List.of(dependencyIndex, applicationIndex)) {
            List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
            List<ReflectiveFieldBuildItem> fields = new ArrayList<>();
            new JimmerProcessor().registerGeneratedTableReflection(
                    new CombinedIndexBuildItem(applicationIndex, computingIndex), reflection::add, fields::add);
            Set<String> expected = computingIndex == dependencyIndex
                    ? Set.of(CdiBookTable.class.getName(), CdiBookTableEx.class.getName())
                    : Set.of();
            assertEquals(expected, reflection.stream().flatMap(item -> item.getClassNames().stream())
                    .collect(Collectors.toSet()));
            assertEquals(expected.stream().map(name -> name + "#$").collect(Collectors.toSet()),
                    fields.stream().map(field -> field.getDeclaringClass() + "#" + field.getName())
                            .collect(Collectors.toSet()));
            for (var item : reflection) {
                assertTrue(item.isConstructors(), "TableProxies invokes wrapper and delayed-operation constructors");
                assertFalse(item.isMethods());
                assertFalse(item.isFields(), "Only the static $ field needs reflective access");
            }
        }
    }

    @Test
    void registersWeakJoinNoArgConstructorsIncludingPrivateOnesAndReferencedParsers() throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : List.of(WeakJoin.class, AbstractJoin.class, PrivateJoin.class, ParameterizedJoin.class,
                ParsedJoin.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        Indexer dependencies = new Indexer();
        dependencies.indexClass(JoinParser.class);
        List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
        new JimmerProcessor().registerWeakJoinConstructors(new CombinedIndexBuildItem(index, dependencies.complete()),
                constructors::add);
        assertEquals(Set.of(PrivateJoin.class.getName(), ParsedJoin.class.getName(), JoinParser.class.getName()),
                constructors.stream().map(ReflectiveMethodBuildItem::getDeclaringClass).collect(Collectors.toSet()));
        for (var constructor : constructors) {
            assertEquals("<init>", constructor.getName());
            assertEquals(0, constructor.getParams().length);
        }
    }

    @Test
    void registersConfiguredCustomDialectPublicNoArgConstructors() throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : List.of(Dialect.class, DefaultDialect.class, AbstractDialect.class, CustomDialect.class,
                ParameterizedDialect.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
        List<ReflectiveClassConditionBuildItem> conditions = new ArrayList<>();
        new JimmerProcessor().registerCustomDialectConstructors(new CombinedIndexBuildItem(index, index), constructors::add,
                conditions::add);
        assertTrue(conditions.isEmpty());
        assertEquals(1, constructors.size());
        assertEquals(CustomDialect.class.getName(), constructors.get(0).getDeclaringClass());
        assertEquals(0, constructors.get(0).getParams().length);
    }

    @Test
    void customDialectSubclassesRetainOptionalDriverGuards() throws Exception {
        var optional = Map.of(CustomPostgresDialect.class, "org.postgresql.util.PGobject",
                CustomH2Dialect.class, "org.h2.value.ValueJson");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        for (var entry : optional.entrySet()) {
            Indexer indexer = new Indexer();
            for (Class<?> type : List.of(Dialect.class, DefaultDialect.class, entry.getKey().getSuperclass(), entry.getKey())) {
                indexer.indexClass(type);
            }
            var index = indexer.complete();
            Path directory = optionalClasses.resolve(entry.getKey().getSimpleName());
            Path marker = directory.resolve(entry.getValue().replace('.', '/') + ".class");
            Files.createDirectories(marker.getParent());
            Files.write(marker, new byte[0]);
            for (boolean runtime : List.of(false, true)) {
                List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
                List<ReflectiveClassConditionBuildItem> conditions = new ArrayList<>();
                try (QuarkusClassLoader loader = QuarkusClassLoader.builder("Custom optional dialect", previous, false)
                        .addNormalPriorityElement(ClassPathElement.fromPath(directory, runtime)).build()) {
                    Thread.currentThread().setContextClassLoader(loader);
                    new JimmerProcessor().registerCustomDialectConstructors(new CombinedIndexBuildItem(index, index),
                            constructors::add, conditions::add);
                } finally {
                    Thread.currentThread().setContextClassLoader(previous);
                }
                assertEquals(runtime ? 1 : 0, constructors.size());
                assertEquals(runtime ? Map.of() : Map.of(entry.getKey().getName(), entry.getValue()),
                        conditions.stream().collect(Collectors.toMap(ReflectiveClassConditionBuildItem::getClassName,
                                ReflectiveClassConditionBuildItem::getTypeReached)));
            }
        }
    }

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
                        ReflectiveClassConditionBuildItem::getClassName, ReflectiveClassConditionBuildItem::getTypeReached));
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

    private abstract static class AbstractJoin implements WeakJoin<CdiBookTable, CdiBookTable> {
        @Override
        public Predicate on(CdiBookTable source, CdiBookTable target) {
            return null;
        }
    }

    private static class PrivateJoin extends AbstractJoin {
        private PrivateJoin() {
        }
    }

    private static class ParameterizedJoin extends AbstractJoin {
        ParameterizedJoin(String value) {
        }
    }

    @UsingWeakJoinMetadataParser(JoinParser.class)
    private static class ParsedJoin extends AbstractJoin {
    }

    public static class JoinParser implements WeakJoinMetadataParser {
        @Override
        public WeakJoinMetadata parse(Class<?> weakJoinType) {
            return new WeakJoinMetadata(false, false, CdiBook.class, CdiBook.class);
        }
    }

    public abstract static class AbstractDialect extends DefaultDialect {
    }

    public static class CustomDialect extends AbstractDialect {
    }

    public static class ParameterizedDialect extends DefaultDialect {
        public ParameterizedDialect(String value) {
        }
    }

    public static class CustomPostgresDialect extends org.babyfish.jimmer.sql.dialect.PostgresDialect {
    }

    public static class CustomH2Dialect extends org.babyfish.jimmer.sql.dialect.H2Dialect {
    }
}
