package io.quarkiverse.jimmer.deployment.devui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.Formula;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.LogicalDeleted;
import org.babyfish.jimmer.sql.ManyToOne;
import org.babyfish.jimmer.sql.OneToMany;
import org.babyfish.jimmer.sql.Table;
import org.babyfish.jimmer.sql.Transient;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.jimmer.deployment.RepositoryMetadata;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractKotlinRepository;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.KRepository;
import io.quarkus.agroal.DataSource;

class JimmerDevUIModelTest {

    private static final AtomicBoolean MODEL_INITIALIZED = new AtomicBoolean();

    @Test
    void collectsDeclaredAndInheritedModelWithoutInitializingApplicationClasses() throws IOException {
        Map<String, Object> model = JimmerDevUIModel.create(index(), List.of(), Map.of());
        List<Map<String, Object>> entities = rows(model, "entities");
        assertEquals(1, entities.size());
        Map<String, Object> entity = entities.get(0);
        assertEquals(Book.class.getName(), entity.get("name"));
        assertEquals("Book", entity.get("simpleName"));
        assertEquals("DECLARED_BOOK", entity.get("tableName"));
        assertEquals("catalog", entity.get("microServiceName"));
        assertEquals(List.of(Base.class.getName()), entity.get("superTypes"));

        List<Map<String, Object>> properties = rows(entity, "properties");
        Map<String, Object> id = named(properties, "id");
        assertEquals(Base.class.getName(), id.get("declaredIn"));
        assertEquals(true, id.get("id"));
        assertEquals(false, id.get("nullable"));
        assertEquals(true, named(properties, "deleted").get("logicalDeleted"));
        assertEquals("getName", named(properties, "name").get("getterName"));
        assertEquals(true, named(properties, "name").get("nullable"));
        assertEquals("isPublished", named(properties, "isPublished").get("getterName"));
        assertEquals("ManyToOne", named(properties, "parent").get("association"));
        assertEquals(Book.class.getName(), named(properties, "parent").get("targetType"));
        assertEquals("OneToMany", named(properties, "children").get("association"));
        assertEquals(Book.class.getName(), named(properties, "children").get("targetType"));
        assertTrue(named(properties, "children").get("type").toString().contains("java.util.List<"));
        assertEquals(false, named(properties, "children").get("nullable"));
        assertEquals(true, named(properties, "note").get("transientProperty"));
        assertNotNull(named(properties, "displayName"));
        assertFalse(properties.stream().anyMatch(property -> property.get("name").equals("helper")));
        assertFalse(MODEL_INITIALIZED.get(), "Index inspection must not initialize the model interface");
    }

    @Test
    void usesLegacyMetadataAndOnlyProvableApplicationInjectionBindings() throws IOException {
        Map<String, Object> model = JimmerDevUIModel.create(index(), List.of(
                new RepositoryMetadata(Book.class, LegacyBooks.class, "archive"),
                new RepositoryMetadata(Book.class, LegacyKotlinBooks.class, "kotlin-archive")),
                Map.of(
                        NamedBooks.class.getName(), Book.class.getName(),
                        DefaultBooks.class.getName(), Book.class.getName(),
                        KotlinBooks.class.getName(), Book.class.getName(),
                        ManualBooks.class.getName(), Book.class.getName(),
                        PlainBooks.class.getName(), Book.class.getName(),
                        AmbiguousBooks.class.getName(), Book.class.getName(),
                        QualifiedBooks.class.getName(), Book.class.getName()));
        List<Map<String, Object>> repositories = rows(model, "repositories");
        assertEquals(9, repositories.size());
        Map<String, Object> legacy = named(repositories, LegacyBooks.class.getName());
        assertEquals("legacy", legacy.get("style"));
        assertEquals("java", legacy.get("kind"));
        assertEquals("archive", legacy.get("dataSource"));
        assertEquals("repository-metadata", legacy.get("bindingSource"));
        assertEquals("java.lang.Long", legacy.get("idType"));
        assertEquals("kotlin", named(repositories, LegacyKotlinBooks.class.getName()).get("kind"));
        Map<String, Object> named = named(repositories, NamedBooks.class.getName());
        assertEquals("application", named.get("style"));
        assertEquals("inventory", named.get("dataSource"));
        assertEquals("java.lang.Long", named.get("idType"));
        assertEquals("constructor-parameter", named.get("bindingSource"));
        assertEquals("<default>", named(repositories, DefaultBooks.class.getName()).get("dataSource"));
        assertEquals("kotlin-store", named(repositories, KotlinBooks.class.getName()).get("dataSource"));
        assertEquals("kotlin", named(repositories, KotlinBooks.class.getName()).get("kind"));
        for (Class<?> uncertain : List.of(ManualBooks.class, PlainBooks.class, AmbiguousBooks.class, QualifiedBooks.class)) {
            assertEquals("unknown", named(repositories, uncertain.getName()).get("dataSource"));
        }
        assertEquals("bean-discovery-not-proven", named(repositories, PlainBooks.class.getName()).get("bindingSource"));
    }

    @Test
    void doesNotInventTableNamesOrResolveRepositoriesAbsentFromTheIndex() throws IOException {
        Indexer indexer = new Indexer();
        indexModel(indexer, Base.class, "Entity");
        Map<String, Object> model = JimmerDevUIModel.create(indexer.complete(), List.of(),
                Map.of(PlainBooks.class.getName(), Base.class.getName()));
        assertEquals("", rows(model, "entities").get(0).get("tableName"));
        Map<String, Object> repository = rows(model, "repositories").get(0);
        assertEquals("unknown", repository.get("idType"));
        assertEquals("unknown", repository.get("kind"));
        assertEquals("unknown", repository.get("dataSource"));
        assertEquals("class-not-indexed", repository.get("bindingSource"));
    }

    private static Index index() throws IOException {
        Indexer indexer = new Indexer();
        indexModel(indexer, Base.class, "MappedSuperclass");
        indexModel(indexer, Book.class, "Entity");
        for (Class<?> type : List.of(JRepository.class, KRepository.class, AbstractJavaRepository.class,
                AbstractKotlinRepository.class, LegacyBooks.class, LegacyKotlinBooks.class, GenericBooks.class,
                NamedBooks.class, DefaultBooks.class, KotlinBooks.class, ManualBooks.class, PlainBooks.class,
                AmbiguousBooks.class, QualifiedBooks.class)) {
            indexer.indexClass(type);
        }
        return indexer.complete();
    }

    // Add model markers only to indexed bytecode, so these fixtures need no APT-generated model classes.
    private static void indexModel(Indexer indexer, Class<?> type, String annotation) throws IOException {
        try (InputStream input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            ClassReader reader = new ClassReader(input);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public void visitEnd() {
                    AnnotationVisitor marker = visitAnnotation("Lorg/babyfish/jimmer/sql/" + annotation + ";", true);
                    if (type == Book.class) {
                        marker.visit("microServiceName", "catalog");
                    }
                    marker.visitEnd();
                    super.visitEnd();
                }
            }, 0);
            indexer.index(new ByteArrayInputStream(writer.toByteArray()));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> model, String key) {
        return (List<Map<String, Object>>) model.get(key);
    }

    private static Map<String, Object> named(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(row -> row.get("name").equals(name)).findFirst().orElseThrow();
    }

    interface Base {
        @Id
        long id();

        @LogicalDeleted("true")
        boolean deleted();
    }

    @Table(name = "DECLARED_BOOK")
    interface Book extends Base {
        boolean INITIALIZED = MODEL_INITIALIZED.getAndSet(true);

        @Nullable
        String getName();

        boolean isPublished();

        @Nullable
        @ManyToOne
        Book parent();

        @OneToMany(mappedBy = "parent")
        List<Book> children();

        @Transient
        String note();

        @Formula(dependencies = "getName")
        default String displayName() {
            return getName();
        }

        default int helper() {
            return 0;
        }
    }

    interface LegacyBooks extends JRepository<Book, Long> {
    }

    interface LegacyKotlinBooks extends KRepository<Book, Long> {
    }

    abstract static class GenericBooks<E, ID> extends AbstractJavaRepository<E, ID> {
        GenericBooks(JSqlClient client) {
            super(client);
        }
    }

    @Singleton
    static class NamedBooks extends GenericBooks<Book, Long> {
        NamedBooks(@DataSource("inventory") JSqlClient client) {
            super(client);
        }
    }

    @Singleton
    static class DefaultBooks extends AbstractJavaRepository<Book, Long> {
        DefaultBooks(JSqlClient client) {
            super(client);
        }
    }

    @Singleton
    static class KotlinBooks extends AbstractKotlinRepository<Book, Long> {
        KotlinBooks(@DataSource("kotlin-store") KSqlClient client) {
            super(client);
        }
    }

    @DataSource("not-an-injection-binding")
    static class ManualBooks extends AbstractJavaRepository<Book, Long> {
        ManualBooks() {
            super(null);
        }
    }

    static class PlainBooks extends AbstractJavaRepository<Book, Long> {
        PlainBooks(JSqlClient client) {
            super(client);
        }
    }

    @Singleton
    static class AmbiguousBooks extends AbstractJavaRepository<Book, Long> {
        @Inject
        @DataSource("other")
        JSqlClient other;

        AmbiguousBooks(JSqlClient client) {
            super(client);
        }
    }

    @Singleton
    static class QualifiedBooks extends AbstractJavaRepository<Book, Long> {
        QualifiedBooks(@CustomQualifier JSqlClient client) {
            super(client);
        }
    }

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface CustomQualifier {
    }
}
