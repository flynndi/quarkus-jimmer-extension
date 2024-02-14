package io.quarkiverse.jimmer.runtime.util;

import jakarta.enterprise.util.TypeLiteral;

import org.babyfish.jimmer.sql.DraftInterceptor;
import org.babyfish.jimmer.sql.filter.Filter;
import org.babyfish.jimmer.sql.kt.filter.KFilter;
import org.babyfish.jimmer.sql.runtime.ScalarProvider;

public class Constant {

    public static final TypeLiteral<Filter<?>> FILTER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public static final TypeLiteral<KFilter<?>> K_FILTER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public static final TypeLiteral<ScalarProvider<?, ?>> SCALAR_PROVIDER_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public static final TypeLiteral<DraftInterceptor<?, ?>> DRAFT_INTERCEPTOR_TYPE_LITERAL = new TypeLiteral<>() {
    };

    public static final String APPLICATION_ZIP = "application/zip";
}
