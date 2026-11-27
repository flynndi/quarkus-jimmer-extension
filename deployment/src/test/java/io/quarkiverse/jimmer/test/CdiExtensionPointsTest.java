package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Vetoed;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.filter.Filter;
import org.babyfish.jimmer.sql.filter.FilterArgs;
import org.babyfish.jimmer.sql.meta.LogicalDeletedLongGenerator;
import org.babyfish.jimmer.sql.meta.LogicalDeletedValueGenerator;
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator;
import org.babyfish.jimmer.sql.meta.UserIdGenerator;
import org.babyfish.jimmer.sql.runtime.Customizer;
import org.babyfish.jimmer.sql.runtime.Initializer;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.SqlClients;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusAopProxyProvider;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkiverse.jimmer.test.model.CdiBookProps;
import io.quarkiverse.jimmer.test.model.deletion.ArchivedBook;
import io.quarkus.arc.Arc;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.BeanDestroyer;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.annotation.Identifier;

class CdiExtensionPointsTest {

    @RegisterExtension
    static final QuarkusExtensionTest APP = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(CdiBook.class.getPackage())
                    .addPackage(ArchivedBook.class.getPackage())
                    .addClasses(BookFilter.class, TransactionalCustomizer.class, TransactionalInitializer.class)
                    .addClasses(BookIdGenerator.class, PreferredBookIdGenerator.class, AmbiguousBookIdGenerator.class,
                            DependentIdGenerator.class,
                            BookResolver.class,
                            NamedBookResolver.class, DependentBookResolver.class, UnmanagedBookResolver.class,
                            SyntheticIdGenerator.class, SyntheticDeletedGenerator.class, SyntheticBookResolver.class)
                    .addAsResource(new StringAsset(CdiBook.class.getName() + "\n" + ArchivedBook.class.getName() + "\n"),
                            "META-INF/jimmer/entities"))
            .addBuildChainCustomizer(buildBuilder -> buildBuilder.addBuildStep(context -> {
                context.produce(SyntheticBeanBuildItem.configure(SyntheticIdGenerator.class)
                        .addType(UserIdGenerator.class)
                        .scope(Dependent.class)
                        .name("syntheticBookIds")
                        .unremovable()
                        .creator(SyntheticIdGenerator.Creator.class)
                        .destroyer(SyntheticIdGenerator.Destroyer.class)
                        .done());
                context.produce(SyntheticBeanBuildItem.configure(SyntheticDeletedGenerator.class)
                        .addType(LogicalDeletedValueGenerator.class)
                        .scope(Dependent.class)
                        .name("syntheticDeletedMarker")
                        .unremovable()
                        .creator(SyntheticDeletedGenerator.Creator.class)
                        .destroyer(SyntheticDeletedGenerator.Destroyer.class)
                        .done());
                context.produce(SyntheticBeanBuildItem.configure(SyntheticBookResolver.class)
                        .addType(TransientResolver.class)
                        .scope(Dependent.class)
                        .name("syntheticResolver")
                        .unremovable()
                        .creator(SyntheticBookResolver.Creator.class)
                        .destroyer(SyntheticBookResolver.Destroyer.class)
                        .done());
            }).produces(SyntheticBeanBuildItem.class).build())
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:cdi-extension-points;DB_CLOSE_DELAY=-1");

    @Inject
    JSqlClient client;

    @Inject
    DataSource dataSource;

    @Inject
    ArchivedBook.MarkerSource markerSource;

    @Test
    void discoversUninjectedSpiBeansAndKeepsTheirInterceptors() {
        JSqlClientImplementor implementation = (JSqlClientImplementor) client;
        assertEquals(7, implementation.getDefaultBatchSize());
        assertNotNull(client.getFilters().getFilter(CdiBook.class));
        TransactionalInitializer initializer = Arc.container().instance(TransactionalInitializer.class).get();
        assertEquals(Status.STATUS_ACTIVE, initializer.status);
        var provider = new QuarkusAopProxyProvider();
        Object customizer = Arc.container().instance(TransactionalCustomizer.class).get();
        assertNotEquals(TransactionalCustomizer.class, customizer.getClass());
        assertEquals(TransactionalCustomizer.class, provider.getTargetClass(customizer));
        assertNotEquals(TransactionalInitializer.class, initializer.getClass());
        assertEquals(TransactionalInitializer.class, provider.getTargetClass(initializer));
    }

    @Test
    void convenienceFactoryUsesTheDefaultDataSourceName() {
        JSqlClientImplementor customClient = (JSqlClientImplementor) SqlClients.java(Arc.container());
        assertEquals(7, customClient.getDefaultBatchSize());
        assertNotNull(customClient.getConnectionManager());
    }

    @Test
    void logicalDeleteUsesALazyGeneratorOwnedByTheClient() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("create table if not exists ARCHIVED_BOOK "
                    + "(ID bigint primary key, DELETED_MARKER bigint not null)");
            statement.executeUpdate("merge into ARCHIVED_BOOK key(ID) values (1, 0)");
            var entities = client.getEntities();
            assertEquals(0, markerSource.generatorsCreated);
            entities.deleteCommand(ArchivedBook.class, 1L).execute(connection);
            try (var result = statement.executeQuery("select DELETED_MARKER from ARCHIVED_BOOK where ID = 1")) {
                assertTrue(result.next());
                assertEquals(73L, result.getLong(1));
            }
        }
        assertEquals(1, markerSource.generatorsCreated);
        assertEquals(0, markerSource.generatorsDestroyed);
        Arc.container().instance(JSqlClient.class).destroy();
        assertEquals(1, markerSource.generatorsDestroyed);
    }

    @Test
    void logicalDeleteFallsBackToJimmerForUnmanagedGenerators() throws Exception {
        var generators = ((JSqlClientImplementor) client).getGeneratorContext();
        var generator = generators.getLogicalDeletedValueGenerator(LogicalDeletedLongGenerator.class);
        assertEquals(LogicalDeletedLongGenerator.class, generator.getClass());
    }

    @Test
    void logicalDeleteReportsMissingAndIncorrectlyTypedBeanNames() {
        var generators = ((JSqlClientImplementor) client).getGeneratorContext();
        var missing = assertThrows(IllegalStateException.class,
                () -> generators.getLogicalDeletedValueGenerator("missingBookGenerator"));
        assertTrue(missing.getMessage().contains("missingBookGenerator"));

        var wrongType = assertThrows(IllegalStateException.class,
                () -> generators.getLogicalDeletedValueGenerator("bookMarkerSource"));
        assertTrue(wrongType.getMessage().contains("bookMarkerSource"));
        assertTrue(wrongType.getMessage().contains(ArchivedBook.MarkerSource.class.getName()));
    }

    @Test
    void resolvesIdGeneratorsByTypeAndName() throws Exception {
        var generators = ((JSqlClientImplementor) client).getGeneratorContext();
        assertInstanceOf(PreferredBookIdGenerator.class, generators.getUserIdGenerator(BookIdGenerator.class));
        assertThrows(AmbiguousResolutionException.class, () -> generators.getUserIdGenerator(AmbiguousBookIdGenerator.class));
        assertEquals(BookIdGenerator.class, generators.getUserIdGenerator("bookIds").getClass());
        assertInstanceOf(UUIDIdGenerator.class, generators.getUserIdGenerator(UUIDIdGenerator.class));
    }

    @Test
    void resolvesQualifiedTransientResolversAndRejectsAmbiguity() throws Exception {
        var resolvers = ((JSqlClientImplementor) client).getTransientResolverProvider();
        var resolver = assertInstanceOf(NamedBookResolver.class, resolverByType(NamedBookResolver.class));
        assertSame(resolver, resolvers.get("bookResolverByName", client));
        assertThrows(AmbiguousResolutionException.class, () -> resolverByType(BookResolver.class));
        assertNull(resolverByType(UnmanagedBookResolver.class));
    }

    @SuppressWarnings("unchecked")
    private TransientResolver<?, ?> resolverByType(Class<? extends TransientResolver<?, ?>> type) throws Exception {
        var resolvers = ((JSqlClientImplementor) client).getTransientResolverProvider();
        return resolvers.get((Class<TransientResolver<?, ?>>) type, client);
    }

    @Test
    void generatorAndResolverErrorsIdentifyTheRequestedBean() {
        var implementation = (JSqlClientImplementor) client;
        var generators = implementation.getGeneratorContext();
        var resolvers = implementation.getTransientResolverProvider();
        String missing = "Cannot resolve a Quarkus bean named \"missingBookStrategy\"";
        assertEquals(missing, assertThrows(IllegalStateException.class,
                () -> generators.getUserIdGenerator("missingBookStrategy")).getMessage());
        assertEquals(missing, assertThrows(IllegalStateException.class,
                () -> resolvers.get("missingBookStrategy", client)).getMessage());

        String wrongType = "Quarkus bean named \"bookMarkerSource\" has type \"" + ArchivedBook.MarkerSource.class.getName()
                + "\", expected \"%s\"";
        assertEquals(wrongType.formatted(UserIdGenerator.class.getName()), assertThrows(IllegalStateException.class,
                () -> generators.getUserIdGenerator("bookMarkerSource")).getMessage());
        assertEquals(wrongType.formatted(TransientResolver.class.getName()), assertThrows(IllegalStateException.class,
                () -> resolvers.get("bookMarkerSource", client)).getMessage());
    }

    @Test
    void releasesDependentNamedGeneratorsAndResolversWithClient() throws Exception {
        var implementation = (JSqlClientImplementor) client;
        int idDestroyedBefore = DependentIdGenerator.destructions;
        int resolverDestroyedBefore = DependentBookResolver.destructions;

        assertInstanceOf(DependentIdGenerator.class,
                implementation.getGeneratorContext().getUserIdGenerator("dependentBookIds"));
        assertInstanceOf(DependentBookResolver.class,
                implementation.getTransientResolverProvider().get("dependentBookResolver", client));
        assertEquals(idDestroyedBefore, DependentIdGenerator.destructions);
        assertEquals(resolverDestroyedBefore, DependentBookResolver.destructions);

        Arc.container().instance(JSqlClient.class).destroy();
        assertEquals(idDestroyedBefore + 1, DependentIdGenerator.destructions);
        assertEquals(resolverDestroyedBefore + 1, DependentBookResolver.destructions);
    }

    @Test
    void manualClientDoesNotLeakDependentPluginsIntoGlobalInstance() throws Exception {
        JSqlClientImplementor manualClient = (JSqlClientImplementor) SqlClients.java(Arc.container());
        int idDestroyedBefore = DependentIdGenerator.destructions;
        int resolverDestroyedBefore = DependentBookResolver.destructions;

        UserIdGenerator<?> idGenerator = manualClient.getGeneratorContext().getUserIdGenerator("dependentBookIds");
        TransientResolver<?, ?> resolver = manualClient.getTransientResolverProvider()
                .get("dependentBookResolver", manualClient);
        assertInstanceOf(DependentIdGenerator.class, idGenerator);
        assertInstanceOf(DependentBookResolver.class, resolver);

        Arc.container().beanManager().createInstance().destroy(idGenerator);
        Arc.container().beanManager().createInstance().destroy(resolver);
        assertEquals(idDestroyedBefore, DependentIdGenerator.destructions);
        assertEquals(resolverDestroyedBefore, DependentBookResolver.destructions);
    }

    @Test
    void resolvesAndReleasesSyntheticCdiNamedPluginsWithoutNamedQualifier() throws Exception {
        JSqlClientImplementor manualClient = (JSqlClientImplementor) SqlClients.java(Arc.container());
        int manualIdDestroyedBefore = SyntheticIdGenerator.destructions;
        UserIdGenerator<?> manualIdGen = manualClient.getGeneratorContext().getUserIdGenerator("syntheticBookIds");
        assertEquals(42L, manualIdGen.generate(CdiBook.class));
        Arc.container().beanManager().createInstance().destroy(manualIdGen);
        assertEquals(manualIdDestroyedBefore, SyntheticIdGenerator.destructions);

        var implementation = (JSqlClientImplementor) client;
        int idDestroyedBefore = SyntheticIdGenerator.destructions;
        int deletedDestroyedBefore = SyntheticDeletedGenerator.destructions;
        int resolverDestroyedBefore = SyntheticBookResolver.destructions;

        assertEquals(42L, implementation.getGeneratorContext().getUserIdGenerator("syntheticBookIds").generate(CdiBook.class));
        assertEquals(84L, implementation.getGeneratorContext()
                .getLogicalDeletedValueGenerator("syntheticDeletedMarker").generate());
        @SuppressWarnings("unchecked")
        TransientResolver<Long, String> resolver = (TransientResolver<Long, String>) implementation
                .getTransientResolverProvider().get("syntheticResolver", client);
        assertEquals(Map.of(1L, "synthetic"), resolver.resolve(java.util.List.of(1L)));

        assertEquals(idDestroyedBefore, SyntheticIdGenerator.destructions);
        assertEquals(deletedDestroyedBefore, SyntheticDeletedGenerator.destructions);
        assertEquals(resolverDestroyedBefore, SyntheticBookResolver.destructions);

        Arc.container().instance(JSqlClient.class).destroy();
        assertEquals(idDestroyedBefore + 1, SyntheticIdGenerator.destructions);
        assertEquals(deletedDestroyedBefore + 1, SyntheticDeletedGenerator.destructions);
        assertEquals(resolverDestroyedBefore + 1, SyntheticBookResolver.destructions);
    }

    public static class SyntheticIdGenerator implements UserIdGenerator<Long> {
        static int destructions;

        @Override
        public Long generate(Class<?> entityType) {
            return 42L;
        }

        public static class Creator implements BeanCreator<SyntheticIdGenerator> {
            @Override
            public SyntheticIdGenerator create(SyntheticCreationalContext<SyntheticIdGenerator> context) {
                return new SyntheticIdGenerator();
            }
        }

        public static class Destroyer implements BeanDestroyer<SyntheticIdGenerator> {
            @Override
            public void destroy(SyntheticIdGenerator instance, SyntheticCreationalContext<SyntheticIdGenerator> context) {
                destructions++;
            }
        }
    }

    public static class SyntheticDeletedGenerator implements LogicalDeletedValueGenerator<Long> {
        static int destructions;

        @Override
        public Long generate() {
            return 84L;
        }

        public static class Creator implements BeanCreator<SyntheticDeletedGenerator> {
            @Override
            public SyntheticDeletedGenerator create(SyntheticCreationalContext<SyntheticDeletedGenerator> context) {
                return new SyntheticDeletedGenerator();
            }
        }

        public static class Destroyer implements BeanDestroyer<SyntheticDeletedGenerator> {
            @Override
            public void destroy(SyntheticDeletedGenerator instance,
                    SyntheticCreationalContext<SyntheticDeletedGenerator> context) {
                destructions++;
            }
        }
    }

    public static class SyntheticBookResolver implements TransientResolver<Long, String> {
        static int destructions;

        @Override
        public Map<Long, String> resolve(Collection<Long> ids) {
            return Map.of(1L, "synthetic");
        }

        public static class Creator implements BeanCreator<SyntheticBookResolver> {
            @Override
            public SyntheticBookResolver create(SyntheticCreationalContext<SyntheticBookResolver> context) {
                return new SyntheticBookResolver();
            }
        }

        public static class Destroyer implements BeanDestroyer<SyntheticBookResolver> {
            @Override
            public void destroy(SyntheticBookResolver instance, SyntheticCreationalContext<SyntheticBookResolver> context) {
                destructions++;
            }
        }
    }

    @Dependent
    @Named("dependentBookIds")
    public static class DependentIdGenerator implements UserIdGenerator<Long> {
        static int destructions;

        @Override
        public Long generate(Class<?> entityType) {
            return 2L;
        }

        @PreDestroy
        void destroy() {
            destructions++;
        }
    }

    @Dependent
    @Named("dependentBookResolver")
    @Identifier("dependent")
    public static class DependentBookResolver implements TransientResolver<Long, String> {
        static int destructions;

        @Override
        public Map<Long, String> resolve(Collection<Long> ids) {
            return Map.of();
        }

        @PreDestroy
        void destroy() {
            destructions++;
        }
    }

    @Singleton
    @Named("bookIds")
    @Identifier("book")
    public static class BookIdGenerator implements UserIdGenerator<Long> {
        @Override
        public Long generate(Class<?> entityType) {
            return 1L;
        }
    }

    @Alternative
    @Priority(1)
    @Singleton
    @Identifier("preferred")
    public static class PreferredBookIdGenerator extends BookIdGenerator {
    }

    @Singleton
    public static class AmbiguousBookIdGenerator extends BookIdGenerator {
        @Produces
        @Identifier("second")
        static AmbiguousBookIdGenerator secondGenerator() {
            return new AmbiguousBookIdGenerator();
        }
    }

    @Singleton
    @Identifier("book")
    public static class BookResolver implements TransientResolver<Long, String> {
        @Override
        public Map<Long, String> resolve(Collection<Long> ids) {
            return Map.of();
        }
    }

    @Singleton
    @Identifier("named")
    @Named("bookResolverByName")
    public static class NamedBookResolver extends BookResolver {
    }

    @Vetoed
    public static class UnmanagedBookResolver extends BookResolver {
    }

    // Deliberately no @Unremovable, @Named, or injection points referring to these beans.
    @ApplicationScoped
    public static class BookFilter implements Filter<CdiBookProps> {
        @Override
        public void filter(FilterArgs<CdiBookProps> args) {
            args.where(args.getTable().name().ne("hidden"));
        }
    }

    @ApplicationScoped
    public static class TransactionalCustomizer implements Customizer {
        @Inject
        TransactionManager transactionManager;

        @Override
        @Transactional
        public void customize(JSqlClient.Builder builder) throws Exception {
            assertEquals(Status.STATUS_ACTIVE, transactionManager.getStatus());
            builder.setDefaultBatchSize(7);
        }
    }

    @Singleton
    public static class TransactionalInitializer implements Initializer {
        @Inject
        TransactionManager transactionManager;
        int status;

        @Override
        @Transactional
        public void initialize(JSqlClient sqlClient) throws Exception {
            status = transactionManager.getStatus();
        }
    }
}
