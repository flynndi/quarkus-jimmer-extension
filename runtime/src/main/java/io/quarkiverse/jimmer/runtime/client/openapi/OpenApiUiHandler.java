package io.quarkiverse.jimmer.runtime.client.openapi;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

import org.babyfish.jimmer.client.meta.ApiService;
import org.babyfish.jimmer.client.meta.Schema;
import org.babyfish.jimmer.client.runtime.impl.MetadataBuilder;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class OpenApiUiHandler implements Handler<RoutingContext> {

    private final JimmerBuildTimeConfig buildTimeConfig;

    private final String resolvedRefPath;

    public OpenApiUiHandler(JimmerBuildTimeConfig buildTimeConfig, String resolvedRefPath) {
        this.buildTimeConfig = buildTimeConfig;
        this.resolvedRefPath = resolvedRefPath;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        String html = this.html(routingContext.request().getParam("groups"));
        HttpServerResponse response = routingContext.response();
        doHandle(response, html);

    }

    private void doHandle(HttpServerResponse response, String html) {
        response.putHeader(HttpHeaders.CONTENT_TYPE, Constant.TEXT_HTML)
                .end(Buffer.buffer(html.getBytes(StandardCharsets.UTF_8)));
    }

    private String html(String groups) {
        String refPath = resolvedRefPath;
        String resource;
        if (buildTimeConfig.client().openapi().refPath().isPresent() || hasMetadata()) {
            resource = refPath != null && !refPath.isEmpty() ? "META-INF/jimmer/openapi/index.html.template"
                    : "META-INF/jimmer/openapi/no-api.html";
        } else {
            resource = "META-INF/jimmer/openapi/no-metadata.html";
        }
        StringBuilder builder = new StringBuilder();
        char[] buf = new char[1024];
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (inputStream == null) {
            throw new IllegalStateException("The resource \"" + resource + "\" does not exist");
        }
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            int len;
            while ((len = reader.read(buf)) != -1) {
                builder.append(buf, 0, len);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read resource \"" + resource + "\"", ex);
        }
        boolean isTemplate = resource.endsWith(".template");
        if (!isTemplate) {
            return builder.toString();
        }
        if (groups != null && !groups.isEmpty()) {
            int fragmentIndex = refPath.indexOf('#');
            String fragment = fragmentIndex < 0 ? "" : refPath.substring(fragmentIndex);
            String location = fragmentIndex < 0 ? refPath : refPath.substring(0, fragmentIndex);
            refPath = location + (location.contains("?") ? "&" : "?")
                    + "groups=" + URLEncoder.encode(groups, StandardCharsets.UTF_8) + fragment;
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
                        "${openapi.refPath}",
                        refPath);
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
