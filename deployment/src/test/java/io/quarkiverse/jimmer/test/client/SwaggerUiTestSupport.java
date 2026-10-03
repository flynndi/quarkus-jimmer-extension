package io.quarkiverse.jimmer.test.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.builder.BuildChainBuilder;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;

final class SwaggerUiTestSupport {

    private static final Pattern LINKED_RESOURCE = Pattern.compile(
            "<(script|link|img)\\b[^>]*?\\b(src|href)\\s*=\\s*([\"'])(.*?)\\3", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED_URL = Pattern.compile(
            "\\{\\s*url:\\s*\"([^\"]+)\"\\s*,\\s*name:\\s*\"([^\"]+)\"\\s*}");

    private SwaggerUiTestSupport() {
    }

    static void assertNoSmallRyeOpenApi(BuildChainBuilder builder) {
        builder.addBuildStep(context -> assertFalse(context.consume(Capabilities.class).isPresent(Capability.SMALLRYE_OPENAPI),
                "Standalone Swagger UI must not install the SmallRye OpenAPI scanner"))
                .consumes(Capabilities.class).produces(ValidationErrorBuildItem.class).build();
    }

    static String assertUiAndAssets(URI uiUri) throws Exception {
        var response = HttpFeatureTestSupport.get(uiUri);
        if (response.statusCode() == 302 || response.statusCode() == 307) {
            uiUri = uiUri.resolve(response.headers().firstValue("location").orElseThrow());
            response = HttpFeatureTestSupport.get(uiUri);
        }
        assertEquals(200, response.statusCode(), uiUri + "\n" + response.body());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("text/html"));
        String html = response.body();
        assertTrue(html.contains("SwaggerUIBundle"), "Expected the actual Swagger UI bootstrap");
        assertFalse(html.contains("${"), "The generated page must not contain unexpanded placeholders");

        var matcher = LINKED_RESOURCE.matcher(html);
        Set<URI> assets = new LinkedHashSet<>();
        while (matcher.find()) {
            String tag = matcher.group(1);
            String attribute = matcher.group(2);
            if (((tag.equalsIgnoreCase("script") || tag.equalsIgnoreCase("img")) && attribute.equalsIgnoreCase("src"))
                    || (tag.equalsIgnoreCase("link") && attribute.equalsIgnoreCase("href"))) {
                URI asset = uiUri.resolve(matcher.group(4));
                assertEquals(uiUri.getAuthority(), asset.getAuthority(), "Swagger assets should be served locally: " + asset);
                assets.add(asset);
            }
        }
        assertTrue(assets.stream().anyMatch(uri -> uri.getPath().endsWith("swagger-ui-bundle.js")), assets.toString());
        assertTrue(assets.stream().anyMatch(uri -> uri.getPath().endsWith("swagger-ui-standalone-preset.js")),
                assets.toString());
        assertTrue(assets.stream().anyMatch(uri -> uri.getPath().endsWith("swagger-ui.css")), assets.toString());
        assertTrue(assets.stream().anyMatch(uri -> uri.getPath().endsWith("favicon.ico")), assets.toString());
        for (URI asset : assets) {
            var content = HttpFeatureTestSupport.getBytes(asset);
            assertEquals(200, content.statusCode(), asset.toString());
            assertTrue(content.body().length > 0, asset.toString());
            String contentType = content.headers().firstValue("content-type").orElse("");
            if (asset.getPath().endsWith(".js")) {
                assertTrue(contentType.contains("javascript"), asset + " " + contentType);
            } else if (asset.getPath().endsWith(".css")) {
                assertTrue(contentType.startsWith("text/css"), asset + " " + contentType);
            } else if (asset.getPath().endsWith(".ico") || asset.getPath().endsWith(".png")) {
                assertTrue(contentType.startsWith("image/"), asset + " " + contentType);
            }
        }
        return html;
    }

    static void assertGroupedSpecifications(URI origin, String html) throws Exception {
        Map<String, URI> specifications = new LinkedHashMap<>();
        var matcher = NAMED_URL.matcher(html);
        while (matcher.find()) {
            specifications.put(matcher.group(2), origin.resolve(matcher.group(1)));
        }
        assertEquals(Set.of("public", "admin"), specifications.keySet());
        for (var entry : specifications.entrySet()) {
            assertEquals("groups=" + entry.getKey(), entry.getValue().getRawQuery());
            var response = HttpFeatureTestSupport.get(entry.getValue());
            assertEquals(200, response.statusCode(), entry.getValue().toString());
            String expectedPath = entry.getKey().equals("public") ? "/app/http-feature/status"
                    : "/app/http-feature/administration";
            String excludedPath = entry.getKey().equals("public") ? "/app/http-feature/administration"
                    : "/app/http-feature/status";
            // This fixture has simple unquoted YAML path keys; anchor the checks to path entries, not descriptions.
            assertTrue(Pattern.compile("(?m)^  " + Pattern.quote(expectedPath) + ":\\s*$")
                    .matcher(response.body()).find(), response.body());
            assertFalse(response.body().contains(excludedPath), response.body());
            assertTrue(response.body().startsWith("openapi:"), response.body());
        }
    }
}
