package io.quarkiverse.jimmer.graphql.apt;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.squareup.javapoet.AnnotationSpec;

class JimmerGraphQLSourceGeneratorTest {

    @Test
    void generateBatchSourceResolvers() {
        JimmerGraphQLSourceModel model = new JimmerGraphQLSourceModel(List.of(
                new JimmerGraphQLSourceType(
                        "com.example",
                        "Author",
                        "com.example.Author",
                        JimmerGraphQLSourceKind.ENTITY,
                        List.of(),
                        List.of(new JimmerGraphQLSourceMethod(
                                "firstName",
                                "firstName",
                                "java.lang.String",
                                false,
                                "java.lang.String",
                                false,
                                false,
                                "firstName",
                                List.of()))),
                new JimmerGraphQLSourceType(
                        "com.example",
                        "BookStore",
                        "com.example.BookStore",
                        JimmerGraphQLSourceKind.ENTITY,
                        List.of(),
                        List.of(
                                new JimmerGraphQLSourceMethod(
                                        "id",
                                        "id",
                                        "java.lang.Long",
                                        false,
                                        "java.lang.Long",
                                        false,
                                        false,
                                        "id",
                                        List.of()),
                                new JimmerGraphQLSourceMethod(
                                        "avgPrice",
                                        "avgPrice",
                                        "java.math.BigDecimal",
                                        false,
                                        "java.math.BigDecimal",
                                        true,
                                        true,
                                        "avgPrice",
                                        List.of()),
                                new JimmerGraphQLSourceMethod(
                                        "newestBooks",
                                        "newestBooks",
                                        "java.util.List<com.example.Book>",
                                        true,
                                        "com.example.Book",
                                        true,
                                        true,
                                        "newestBooks",
                                        List.of()))),
                new JimmerGraphQLSourceType(
                        "com.example",
                        "Book",
                        "com.example.Book",
                        JimmerGraphQLSourceKind.ENTITY,
                        List.of(),
                        List.of(
                                new JimmerGraphQLSourceMethod(
                                        "id",
                                        "id",
                                        "java.lang.Long",
                                        false,
                                        "java.lang.Long",
                                        false,
                                        false,
                                        "id",
                                        List.of()),
                                new JimmerGraphQLSourceMethod(
                                        "store",
                                        "store",
                                        "com.example.BookStore",
                                        false,
                                        "com.example.BookStore",
                                        true,
                                        false,
                                        "store",
                                        List.of()),
                                new JimmerGraphQLSourceMethod(
                                        "authors",
                                        "authors",
                                        "java.util.List<com.example.Author>",
                                        true,
                                        "com.example.Author",
                                        true,
                                        false,
                                        "authors",
                                        List.of())))));

        Map<String, String> generated = new JimmerGraphQLSourceGenerator(model).generate();

        String bookResolver = generated.get("com.example.graphql.BookGqlSourceResolver");
        Assertions.assertNotNull(bookResolver);
        Assertions.assertTrue(bookResolver.contains("@Source(name = \"store\")"));
        Assertions.assertTrue(bookResolver.contains("List<BookGql> sources"));
        Assertions.assertTrue(
                bookResolver.contains("return support.loadFacadeBatch(sources, \"store\", env, BookStoreGql.class);"));
        Assertions.assertTrue(
                bookResolver.contains("return support.loadFacadeListBatch(sources, \"authors\", env, AuthorGql.class);"));

        String storeResolver = generated.get("com.example.graphql.BookStoreGqlSourceResolver");
        Assertions.assertNotNull(storeResolver);
        Assertions.assertTrue(storeResolver.contains("public List<BigDecimal> avgPrice("));
        Assertions.assertTrue(storeResolver.contains("return support.loadValueBatch(sources, \"avgPrice\", env);"));
        Assertions.assertTrue(storeResolver.contains("public List<List<BookGql>> newestBooks("));
        Assertions.assertTrue(
                storeResolver.contains("return support.loadFacadeListBatch(sources, \"newestBooks\", env, BookGql.class);"));

        String registry = generated.get("com.example.graphql.JimmerGraphQLFacadeRegistry");
        Assertions.assertNotNull(registry);
        Assertions.assertTrue(registry.contains("public boolean supportsFacadeType(Class<?> facadeType)"));
        Assertions.assertTrue(registry.contains("public boolean supportsRaw(Object raw)"));
    }

    @Test
    void generateInEntityGraphqlPackageAndPreserveMethodAnnotations() {
        AnnotationSpec deprecated = AnnotationSpec.builder(Deprecated.class).build();
        JimmerGraphQLSourceModel model = new JimmerGraphQLSourceModel(List.of(
                new JimmerGraphQLSourceType(
                        "com.example.graphql",
                        "Book",
                        "com.example.graphql.Book",
                        JimmerGraphQLSourceKind.ENTITY,
                        List.of(),
                        List.of(
                                new JimmerGraphQLSourceMethod(
                                        "title",
                                        "title",
                                        "java.lang.String",
                                        false,
                                        "java.lang.String",
                                        false,
                                        false,
                                        "displayTitle",
                                        List.of(deprecated)),
                                new JimmerGraphQLSourceMethod(
                                        "store",
                                        "store",
                                        "com.example.graphql.Store",
                                        false,
                                        "com.example.graphql.Store",
                                        true,
                                        false,
                                        "bookStore",
                                        List.of(deprecated)))),
                new JimmerGraphQLSourceType(
                        "com.example.graphql",
                        "Store",
                        "com.example.graphql.Store",
                        JimmerGraphQLSourceKind.ENTITY,
                        List.of(),
                        List.of())));

        Map<String, String> generated = new JimmerGraphQLSourceGenerator(model).generate();

        String facade = generated.get("com.example.graphql.BookGql");
        Assertions.assertNotNull(facade);
        Assertions.assertTrue(facade.contains("@Name(\"displayTitle\")"));
        Assertions.assertTrue(facade.contains("@Deprecated"));

        String resolver = generated.get("com.example.graphql.BookGqlSourceResolver");
        Assertions.assertNotNull(resolver);
        Assertions.assertTrue(resolver.contains("@Name(\"bookStore\")"));
        Assertions.assertTrue(resolver.contains("@Source(name = \"bookStore\")"));
        Assertions.assertTrue(resolver.contains("@Deprecated"));

        String registry = generated.get("com.example.graphql.JimmerGraphQLFacadeRegistry");
        Assertions.assertNotNull(registry);
        Assertions.assertTrue(registry.contains("if (facadeType == StoreGql.class)"));
    }
}
