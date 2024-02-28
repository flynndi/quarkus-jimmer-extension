package io.quarkiverse.jimmer.runtime.client.openapi;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.babyfish.jimmer.client.meta.ApiService;
import org.babyfish.jimmer.client.meta.Schema;
import org.babyfish.jimmer.client.runtime.impl.MetadataBuilder;
import org.jboss.logging.Logger;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.client.ts.TypeScriptHandler;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class OpenApiUiHandler implements Handler<RoutingContext> {

    private static final Logger log = Logger.getLogger(TypeScriptHandler.class);

    private JimmerBuildTimeConfig config;

    private boolean setup = false;

    @Override
    public void handle(RoutingContext routingContext) {
        if (!setup) {
            setup();
        }

        String html = this.html(routingContext.request().getParam("groups"));
        HttpServerResponse response = routingContext.response();
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            doHandle(response, html);
        } else {
            requestContext.activate();
            try {
                doHandle(response, html);
            } finally {
                requestContext.terminate();
            }
        }

    }

    private void doHandle(HttpServerResponse response, String html) {
        response.putHeader(HttpHeaders.CONTENT_TYPE, Constant.TEXT_HTML)
                .end(Buffer.buffer(html.getBytes(StandardCharsets.UTF_8)));
    }

    private void setup() {
        Instance<JimmerBuildTimeConfig> configs = CDI.current().select(JimmerBuildTimeConfig.class,
                Default.Literal.INSTANCE);

        if (configs.isUnsatisfied()) {
            config = null;
        } else if (configs.isAmbiguous()) {
            config = configs.iterator().next();
            log.warnf("Multiple JimmerBuildTimeConfig registries present. Using %s with the built in scrape endpoint", configs);
        } else {
            config = configs.get();
        }

        setup = true;
    }

    private String html(String groups) {
        String path = config.client().openapi().path();
        String resource;
        if (hasMetadata()) {
            resource = path != null && !path.isEmpty() ? "META-INF/jimmer/openapi/index.html.template"
                    : "META-INF/jimmer/openapi/no-api.html";
        } else {
            resource = "META-INF/jimmer/openapi/no-metadata.html";
        }
        StringBuilder builder = new StringBuilder();
        char[] buf = new char[1024];
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        assert inputStream != null;
        try (Reader reader = new InputStreamReader(inputStream)) {
            int len;
            if ((len = reader.read(buf)) != -1) {
                builder.append(buf, 0, len);
            }
        } catch (IOException ex) {
            throw new AssertionError("Internal bug: Can read \"" + resource + "\"");
        }
        boolean isTemplate = resource.endsWith(".template");
        if (!isTemplate) {
            return builder.toString();
        }
        if (groups != null && !groups.isEmpty()) {
            path += "?groups=" + URLEncoder.encode(groups, StandardCharsets.UTF_8);
        }
        return builder
                .toString()
                .replace("${openapi.css}",
                        exists(Constant.CSS_RESOURCE) ? Constant.CSS_URL
                                : "https://unpkg.com/swagger-ui-dist@5.10.5/swagger-ui.css")
                .replace("${openapi.js}",
                        exists(Constant.JS_RESOURCE) ? Constant.JS_URL
                                : "https://unpkg.com/swagger-ui-dist@5.10.5/swagger-ui-bundle.js")
                .replace(
                        "${openapi.path}",
                        path);
    }

    private boolean hasMetadata() {
        Schema schema = MetadataBuilder.loadSchema(Collections.emptySet());
        for (ApiService service : schema.getApiServiceMap().values()) {
            if (!service.getOperations().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean exists(String resource) {
        Enumeration<URL> enumeration;
        try {
            enumeration = OpenApiUiHandler.class.getClassLoader().getResources(resource);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to check the existence of resource \"" + resource + "\"");
        }
        return enumeration.hasMoreElements();
    }
}
