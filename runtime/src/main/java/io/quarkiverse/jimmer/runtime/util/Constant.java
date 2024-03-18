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

    public static final String CSS_RESOURCE = "META-INF/jimmer/swagger/swagger-ui.css";

    public static final String JS_RESOURCE = "META-INF/jimmer/swagger/swagger-ui.js";

    public static final String CLIENT_RESOURCE = "META-INF/jimmer/client";

    public static final String ENTITIES_RESOURCE = "META-INF/jimmer/entities";

    public static final String IMMUTABLES_RESOURCE = "META-INF/jimmer/immutables";

    public static final String TEMPLATE_RESOURCE = "META-INF/jimmer/openapi/index.html.template";

    public static final String NO_API_RESOURCE = "META-INF/jimmer/openapi/no-api.html";

    public static final String NO_METADATA_RESOURCE = "META-INF/jimmer/openapi/no-metadata.html";

    public static final String CSS_URL = "/jimmer-client/swagger-ui.css";

    public static final String JS_URL = "/jimmer-client/swagger-ui.js";

    public static final String APPLICATION_ZIP = "application/zip";

    public static final String TEXT_CSS = "text/css";

    public static final String TEXT_JAVASCRIPT = "text/javascript";

    public static final String TEXT_HTML = "text/html";

    public static final String APPLICATION_YML = "application/yml";

    public static final String BY_IDS = "/jimmerMicroServiceBridge/byIds";

    public static final String BY_ASSOCIATED_IDS = "/jimmerMicroServiceBridge/byAssociatedIds";

    public static final String IDS = "ids";

    public static final String PROP = "prop";

    public static final String TARGET_IDS = "targetIds";

    public static final String FETCHER = "fetcher";
}
