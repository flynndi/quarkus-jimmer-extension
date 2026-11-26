package io.quarkiverse.jimmer.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.babyfish.jimmer.sql.ast.impl.table.TableImplementor;
import org.babyfish.jimmer.sql.ast.table.WeakJoin;
import org.babyfish.jimmer.sql.ast.table.spi.AbstractTypedTable;
import org.babyfish.jimmer.sql.ast.table.spi.UsingWeakJoinMetadataParser;
import org.babyfish.jimmer.sql.ast.table.spi.WeakJoinMetadata;
import org.babyfish.jimmer.sql.ast.table.spi.WeakJoinMetadataParser;
import org.babyfish.jimmer.sql.dialect.ApplicationDialect;
import org.babyfish.jimmer.sql.dialect.DefaultDialect;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.dialect.MySql5Dialect;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.MySqlStyleDialect;
import org.babyfish.jimmer.sql.dialect.OracleDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.dialect.SQLiteDialect;
import org.babyfish.jimmer.sql.dialect.SqlServerDialect;
import org.babyfish.jimmer.sql.dialect.TiDBDialect;
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
            List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
            List<ReflectiveFieldBuildItem> fields = new ArrayList<>();
            new JimmerProcessor().registerGeneratedTableReflection(
                    new CombinedIndexBuildItem(applicationIndex, computingIndex), constructors::add, fields::add);
            boolean tablesPresent = computingIndex == dependencyIndex;
            String tableEx = CdiBookTableEx.class.getName();
            assertEquals(tablesPresent ? Set.of(
                    new ReflectiveMethodBuildItem(tableEx, "<init>", TableImplementor.class),
                    new ReflectiveMethodBuildItem(tableEx, "<init>", AbstractTypedTable.DelayedOperation.class)) : Set.of(),
                    Set.copyOf(constructors));
            assertEquals(tablesPresent ? Set.of(CdiBookTable.class.getName() + "#$", tableEx + "#$") : Set.of(),
                    fields.stream().map(field -> field.getDeclaringClass() + "#" + field.getName())
                            .collect(Collectors.toSet()));
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
                ParameterizedDialect.class, PrivateDialect.class, ApplicationDialect.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
        List<ReflectiveClassConditionBuildItem> conditions = new ArrayList<>();
        new JimmerProcessor().registerDialectConstructors(new CombinedIndexBuildItem(index, index), constructors::add,
                conditions::add);
        assertTrue(conditions.isEmpty());
        assertEquals(Set.of(CustomDialect.class.getName(), ApplicationDialect.class.getName()),
                constructors.stream().map(ReflectiveMethodBuildItem::getDeclaringClass).collect(Collectors.toSet()));
        for (var constructor : constructors) {
            assertEquals("<init>", constructor.getName());
            assertEquals(0, constructor.getParams().length);
        }
    }

    @Test
    void optionalDialectsAndTheirSubclassesRequireTheRuntimeDriver() throws Exception {
        Indexer indexer = new Indexer();
        for (Class<?> type : List.of(Dialect.class, DefaultDialect.class, H2Dialect.class, PostgresDialect.class,
                MySqlStyleDialect.class, MySqlDialect.class, MySql5Dialect.class, OracleDialect.class, SQLiteDialect.class,
                SqlServerDialect.class, TiDBDialect.class, AbstractPostgresDialect.class, CustomPostgresDialect.class,
                CustomH2Dialect.class, ParameterizedH2Dialect.class)) {
            indexer.indexClass(type);
        }
        var index = indexer.complete();
        var optional = Map.of(
                PostgresDialect.class, "org.postgresql.util.PGobject",
                AbstractPostgresDialect.class, "org.postgresql.util.PGobject",
                CustomPostgresDialect.class, "org.postgresql.util.PGobject",
                H2Dialect.class, "org.h2.value.ValueJson",
                CustomH2Dialect.class, "org.h2.value.ValueJson",
                ParameterizedH2Dialect.class, "org.h2.value.ValueJson");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        for (String driver : Set.copyOf(optional.values())) {
            Path directory = optionalClasses.resolve(driver);
            Path marker = directory.resolve(driver.replace('.', '/') + ".class");
            Files.createDirectories(marker.getParent());
            // Presence detection reads resources, not driver classes or their static initializers.
            Files.write(marker, new byte[0]);
            for (boolean runtime : List.of(false, true)) {
                List<ReflectiveMethodBuildItem> constructors = new ArrayList<>();
                List<ReflectiveClassConditionBuildItem> conditions = new ArrayList<>();
                try (QuarkusClassLoader loader = QuarkusClassLoader.builder("Optional dialect dependencies", previous, false)
                        .addNormalPriorityElement(ClassPathElement.fromPath(directory, runtime)).build()) {
                    Thread.currentThread().setContextClassLoader(loader);
                    new JimmerProcessor().registerDialectConstructors(new CombinedIndexBuildItem(index, index),
                            constructors::add, conditions::add);
                } finally {
                    Thread.currentThread().setContextClassLoader(previous);
                }
                assertEquals(optional.entrySet().stream().filter(entry -> !runtime || !driver.equals(entry.getValue()))
                        .collect(Collectors.toMap(entry -> entry.getKey().getName(), Map.Entry::getValue)),
                        conditions.stream().collect(Collectors.toMap(ReflectiveClassConditionBuildItem::getClassName,
                                ReflectiveClassConditionBuildItem::getTypeReached)));
                var expected = new HashSet<Class<?>>(List.of(MySqlDialect.class, MySql5Dialect.class,
                        OracleDialect.class, SQLiteDialect.class, SqlServerDialect.class, TiDBDialect.class));
                if (runtime) {
                    for (var type : List.of(PostgresDialect.class, CustomPostgresDialect.class, H2Dialect.class,
                            CustomH2Dialect.class)) {
                        if (driver.equals(optional.get(type))) {
                            expected.add(type);
                        }
                    }
                }
                assertEquals(expected.stream().map(Class::getName).collect(Collectors.toSet()),
                        constructors.stream().map(ReflectiveMethodBuildItem::getDeclaringClass).collect(Collectors.toSet()));
                for (var constructor : constructors) {
                    assertEquals("<init>", constructor.getName());
                    assertEquals(0, constructor.getParams().length);
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

    public static class PrivateDialect extends DefaultDialect {
        private PrivateDialect() {
        }
    }

    public abstract static class AbstractPostgresDialect extends PostgresDialect {
    }

    public static class CustomPostgresDialect extends AbstractPostgresDialect {
    }

    public static class ParameterizedH2Dialect extends H2Dialect {
        public ParameterizedH2Dialect(String value) {
        }
    }

    public static class CustomH2Dialect extends H2Dialect {
    }
}
