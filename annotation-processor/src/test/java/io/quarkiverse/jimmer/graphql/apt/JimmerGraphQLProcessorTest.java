package io.quarkiverse.jimmer.graphql.apt;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JimmerGraphQLProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void generateFacadeSourcesFromEntityInterfaces() throws Exception {
        Path sourceDir = tempDir.resolve("src");
        write(sourceDir.resolve("org/babyfish/jimmer/sql/Entity.java"), """
                package org.babyfish.jimmer.sql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Entity {}
                """);
        write(sourceDir.resolve("org/babyfish/jimmer/sql/MappedSuperclass.java"), """
                package org.babyfish.jimmer.sql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface MappedSuperclass {}
                """);
        write(sourceDir.resolve("org/babyfish/jimmer/sql/ManyToOne.java"), """
                package org.babyfish.jimmer.sql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface ManyToOne {}
                """);
        write(sourceDir.resolve("org/babyfish/jimmer/sql/OneToMany.java"), """
                package org.babyfish.jimmer.sql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface OneToMany {
                    String mappedBy() default "";
                }
                """);
        write(sourceDir.resolve("org/babyfish/jimmer/sql/Transient.java"), """
                package org.babyfish.jimmer.sql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.METHOD)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Transient {}
                """);
        write(sourceDir.resolve("org/eclipse/microprofile/graphql/Type.java"), """
                package org.eclipse.microprofile.graphql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Type {
                    String value();
                }
                """);
        write(sourceDir.resolve("org/eclipse/microprofile/graphql/GraphQLApi.java"), """
                package org.eclipse.microprofile.graphql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface GraphQLApi {}
                """);
        write(sourceDir.resolve("org/eclipse/microprofile/graphql/Name.java"), """
                package org.eclipse.microprofile.graphql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target({ElementType.METHOD, ElementType.PARAMETER})
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Name {
                    String value();
                }
                """);
        write(sourceDir.resolve("org/eclipse/microprofile/graphql/Source.java"), """
                package org.eclipse.microprofile.graphql;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.PARAMETER)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Source {
                    String name() default "";
                }
                """);
        write(sourceDir.resolve("jakarta/inject/Inject.java"), """
                package jakarta.inject;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Inject {}
                """);
        write(sourceDir.resolve("jakarta/inject/Singleton.java"), """
                package jakarta.inject;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Singleton {}
                """);
        write(sourceDir.resolve("io/quarkus/arc/Unremovable.java"), """
                package io.quarkus.arc;

                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Unremovable {}
                """);
        write(sourceDir.resolve("graphql/schema/DataFetchingEnvironment.java"), """
                package graphql.schema;

                public interface DataFetchingEnvironment {}
                """);
        write(sourceDir.resolve("io/smallrye/graphql/api/Context.java"), """
                package io.smallrye.graphql.api;

                public interface Context {
                    <T> T unwrap(Class<T> type);
                }
                """);
        write(sourceDir.resolve("io/quarkiverse/jimmer/runtime/graphql/facade/JimmerGraphQLFacade.java"), """
                package io.quarkiverse.jimmer.runtime.graphql.facade;

                public interface JimmerGraphQLFacade<T> {
                    T __raw();
                }
                """);
        write(sourceDir.resolve("io/quarkiverse/jimmer/runtime/graphql/facade/JimmerGraphQLGeneratedFacadeRegistry.java"), """
                package io.quarkiverse.jimmer.runtime.graphql.facade;

                public interface JimmerGraphQLGeneratedFacadeRegistry {
                    <T> T wrap(Object raw, Class<T> facadeType);
                    Object wrap(Object raw);
                }
                """);
        write(sourceDir.resolve("io/quarkiverse/jimmer/runtime/graphql/facade/JimmerGraphQLFacadeSupport.java"),
                """
                        package io.quarkiverse.jimmer.runtime.graphql.facade;

                        import java.util.List;

                        import graphql.schema.DataFetchingEnvironment;

                        public class JimmerGraphQLFacadeSupport {
                            public <T> List<T> loadFacadeBatch(List<?> sources, String prop, DataFetchingEnvironment env, Class<T> facadeType) {
                                return java.util.List.of();
                            }

                            public <T> List<List<T>> loadFacadeListBatch(List<?> sources, String prop, DataFetchingEnvironment env, Class<T> facadeType) {
                                return java.util.List.of();
                            }

                            public <T> List<T> loadValueBatch(List<?> sources, String prop, DataFetchingEnvironment env) {
                                return java.util.List.of();
                            }
                        }
                        """);
        write(sourceDir.resolve("com/example/BaseEntity.java"), """
                package com.example;

                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                public interface BaseEntity {
                    long id();
                }
                """);
        write(sourceDir.resolve("com/example/BookStore.java"), """
                package com.example;

                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.OneToMany;

                @Entity
                public interface BookStore extends BaseEntity {
                    String name();

                    @OneToMany(mappedBy = "store")
                    java.util.List<Book> books();
                }
                """);
        write(sourceDir.resolve("com/example/Book.java"), """
                package com.example;

                import java.math.BigDecimal;
                import org.babyfish.jimmer.sql.Entity;
                import org.babyfish.jimmer.sql.ManyToOne;
                import org.babyfish.jimmer.sql.Transient;

                @Entity
                public interface Book extends BaseEntity {
                    String name();
                    int edition();

                    @ManyToOne
                    BookStore store();

                    @Transient
                    BigDecimal avgPrice();
                }
                """);

        Path generatedDir = tempDir.resolve("generated");
        compile(sourceDir, generatedDir);

        String bookFacade = Files.readString(generatedDir.resolve(
                "io/quarkiverse/jimmer/generated/graphql/model/BookGql.java"));
        Assertions.assertTrue(bookFacade.contains("public long getId()"));
        Assertions.assertTrue(bookFacade.contains("return raw.id();"));
        Assertions.assertTrue(bookFacade.contains("public String getName()"));
        Assertions.assertTrue(bookFacade.contains("public int getEdition()"));

        String bookResolver = Files.readString(generatedDir.resolve(
                "io/quarkiverse/jimmer/generated/graphql/resolver/BookGqlSourceResolver.java"));
        Assertions.assertTrue(
                bookResolver.contains("return support.loadFacadeBatch(sources, \"store\", env, BookStoreGql.class);"));
        Assertions.assertTrue(bookResolver.contains("return support.loadValueBatch(sources, \"avgPrice\", env);"));

        String registry = Files.readString(generatedDir.resolve(
                "io/quarkiverse/jimmer/generated/graphql/registry/JimmerGraphQLFacadeRegistry.java"));
        Assertions.assertTrue(registry.contains("if (raw instanceof Book value)"));
        Assertions.assertTrue(registry.contains("if (raw instanceof BookStore value)"));
    }

    private void compile(Path sourceDir, Path generatedDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assertions.assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter output = new StringWriter();
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(generatedDir);
        Files.createDirectories(classesDir);
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            List<Path> sourceFiles = Files.walk(sourceDir)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            Iterable<? extends JavaFileObject> units = fileManager
                    .getJavaFileObjectsFromFiles(sourceFiles.stream().map(Path::toFile).toList());
            List<String> options = List.of(
                    "-source", "17",
                    "-target", "17",
                    "-proc:only",
                    "-s", generatedDir.toString(),
                    "-d", classesDir.toString());
            JavaCompiler.CompilationTask task = compiler.getTask(output, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new JimmerGraphQLProcessor()));
            boolean success = Boolean.TRUE.equals(task.call());
            if (!success) {
                StringBuilder message = new StringBuilder(output.toString());
                diagnostics.getDiagnostics().forEach(diagnostic -> message.append('\n').append(diagnostic));
                Assertions.fail("Compilation failed:" + message);
            }
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
