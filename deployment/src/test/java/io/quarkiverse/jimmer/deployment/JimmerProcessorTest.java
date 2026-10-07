package io.quarkiverse.jimmer.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;

class JimmerProcessorTest {

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
