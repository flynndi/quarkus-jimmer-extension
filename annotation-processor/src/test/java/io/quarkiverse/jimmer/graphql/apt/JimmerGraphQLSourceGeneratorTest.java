package io.quarkiverse.jimmer.graphql.apt;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
                                        List.of()),
                                new JimmerGraphQLSourceMethod(
                                        "avgPrice",
                                        "avgPrice",
                                        "java.math.BigDecimal",
                                        false,
                                        "java.math.BigDecimal",
                                        true,
                                        true,
                                        List.of()),
                                new JimmerGraphQLSourceMethod(
                                        "newestBooks",
                                        "newestBooks",
                                        "java.util.List<com.example.Book>",
                                        true,
                                        "com.example.Book",
                                        true,
                                        true,
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
                                        List.of()),
                                new JimmerGraphQLSourceMethod(
                                        "store",
                                        "store",
                                        "com.example.BookStore",
                                        false,
                                        "com.example.BookStore",
                                        true,
                                        false,
                                        List.of()),
                                new JimmerGraphQLSourceMethod(
                                        "authors",
                                        "authors",
                                        "java.util.List<com.example.Author>",
                                        true,
                                        "com.example.Author",
                                        true,
                                        false,
                                        List.of())))));

        Map<String, String> generated = new JimmerGraphQLSourceGenerator(model).generate();

        String bookResolver = generated.get(
                "io.quarkiverse.jimmer.generated.graphql.resolver.BookGqlSourceResolver");
        Assertions.assertNotNull(bookResolver);
        Assertions.assertTrue(bookResolver.contains(
                "@Source(name = \"store\") java.util.List<io.quarkiverse.jimmer.generated.graphql.model.BookGql> sources"));
        Assertions.assertTrue(bookResolver.contains(
                "return support.loadFacadeBatch(sources, \"store\", env, io.quarkiverse.jimmer.generated.graphql.model.BookStoreGql.class);"));
        Assertions.assertTrue(bookResolver.contains(
                "return support.loadFacadeListBatch(sources, \"authors\", env, io.quarkiverse.jimmer.generated.graphql.model.AuthorGql.class);"));

        String storeResolver = generated.get(
                "io.quarkiverse.jimmer.generated.graphql.resolver.BookStoreGqlSourceResolver");
        Assertions.assertNotNull(storeResolver);
        Assertions.assertTrue(storeResolver.contains("public java.util.List<java.math.BigDecimal> avgPrice("));
        Assertions.assertTrue(storeResolver.contains("return support.loadValueBatch(sources, \"avgPrice\", env);"));
        Assertions.assertTrue(storeResolver.contains(
                "public java.util.List<java.util.List<io.quarkiverse.jimmer.generated.graphql.model.BookGql>> newestBooks("));
        Assertions.assertTrue(storeResolver.contains(
                "return support.loadFacadeListBatch(sources, \"newestBooks\", env, io.quarkiverse.jimmer.generated.graphql.model.BookGql.class);"));
    }
}
