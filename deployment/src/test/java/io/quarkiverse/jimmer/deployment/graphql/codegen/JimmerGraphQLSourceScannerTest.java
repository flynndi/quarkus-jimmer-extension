package io.quarkiverse.jimmer.deployment.graphql.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JimmerGraphQLSourceScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scanSiblingKotlinSources() throws Exception {
        Path kotlinDir = tempDir.resolve("src/main/kotlin");

        write(kotlinDir.resolve("com/example/BaseEntity.kt"), """
                package com.example;

                import org.babyfish.jimmer.sql.MappedSuperclass;

                @MappedSuperclass
                interface BaseEntity {

                    val id: Long
                }
                """);

        write(kotlinDir.resolve("com/example/BookStore.kt"), """
                package com.example

                import com.example.BaseEntity
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.OneToMany

                @Entity
                interface BookStore : BaseEntity {
                    val name: String

                    @OneToMany(mappedBy = "store")
                    val books: List<Book>
                }
                """);

        write(kotlinDir.resolve("com/example/Book.kt"), """
                package com.example

                import java.math.BigDecimal
                import com.example.BaseEntity
                import org.babyfish.jimmer.sql.Entity
                import org.babyfish.jimmer.sql.ManyToOne
                import org.babyfish.jimmer.sql.Transient

                @Entity
                interface Book : BaseEntity {
                    val name: String
                    val edition: Int
                    val isPublished: Boolean

                    @ManyToOne
                    val store: BookStore?

                    @Transient
                    val avgPrice: BigDecimal?
                }
                """);

        List<JimmerGraphQLSourceType> types = new JimmerGraphQLKotlinSourceScanner().scan(kotlinDir);
        JimmerGraphQLSourceModel model = new JimmerGraphQLSourceModel(types);

        Assertions.assertNotNull(model.type("com.example.BaseEntity"));
        Assertions.assertNotNull(model.type("com.example.Book"));
        Assertions.assertNotNull(model.type("com.example.BookStore"));

        Map<String, JimmerGraphQLSourceMethod> bookScalarMethods = byName(model.scalarMethods(model.type("com.example.Book")));
        Map<String, JimmerGraphQLSourceMethod> bookComplexMethods = byName(
                model.complexMethods(model.type("com.example.Book")));
        Map<String, JimmerGraphQLSourceMethod> storeComplexMethods = byName(
                model.complexMethods(model.type("com.example.BookStore")));

        Assertions.assertEquals("getId", bookScalarMethods.get("id").rawAccessorName());
        Assertions.assertEquals("getName", bookScalarMethods.get("name").rawAccessorName());
        Assertions.assertEquals("java.lang.Integer", bookScalarMethods.get("edition").returnType());
        Assertions.assertEquals("isPublished", bookScalarMethods.get("isPublished").rawAccessorName());
        Assertions.assertEquals("com.example.BookStore", bookComplexMethods.get("store").returnType());
        Assertions.assertEquals("java.math.BigDecimal", bookComplexMethods.get("avgPrice").returnType());
        Assertions.assertEquals("java.util.List<com.example.Book>", storeComplexMethods.get("books").returnType());
        Assertions.assertEquals("com.example.Book", storeComplexMethods.get("books").elementType());
    }

    private static Map<String, JimmerGraphQLSourceMethod> byName(List<JimmerGraphQLSourceMethod> methods) {
        return methods.stream().collect(Collectors.toMap(
                JimmerGraphQLSourceMethod::name,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
