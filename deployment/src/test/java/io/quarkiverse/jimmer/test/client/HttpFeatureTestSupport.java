package io.quarkiverse.jimmer.test.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.ClientException;
import org.babyfish.jimmer.client.meta.TypeName;
import org.babyfish.jimmer.error.CodeBasedRuntimeException;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import io.quarkiverse.jimmer.runtime.util.Constant;

public final class HttpFeatureTestSupport {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpFeatureTestSupport() {
    }

    static void addClasses(JavaArchive archive) {
        archive.addClasses(HttpFeatureTestSupport.class, Endpoint.class, Failure.class);
    }

    static void addMetadata(JavaArchive archive) {
        addClasses(archive);
        archive.addAsResource(new StringAsset("{\"services\":[{\"typeName\":\""
                + TypeName.of(Endpoint.class).toString(true)
                + "\",\"operations\":[{\"name\":\"status\",\"key\":\"status\",\"parameters\":[],"
                + "\"returnType\":{\"typeName\":\"java.lang.String\"}}]}],\"definitions\":[]}"), Constant.CLIENT_RESOURCE);
    }

    static void addGroupedMetadata(JavaArchive archive) {
        addClasses(archive);
        archive.addClass(AdministrationEndpoint.class);
        archive.addAsResource(new StringAsset("{\"services\":[{\"typeName\":\""
                + TypeName.of(Endpoint.class).toString(true)
                + "\",\"groups\":[\"public\"],\"operations\":["
                + "{\"name\":\"status\",\"key\":\"status\",\"groups\":[\"public\"],\"parameters\":[],"
                + "\"returnType\":{\"typeName\":\"java.lang.String\"}}]},"
                + "{\"typeName\":\"" + TypeName.of(AdministrationEndpoint.class).toString(true)
                + "\",\"groups\":[\"admin\"],\"operations\":["
                + "{\"name\":\"administration\",\"key\":\"administration\",\"groups\":[\"admin\"],\"parameters\":[],"
                + "\"returnType\":{\"typeName\":\"java.lang.String\"}}]}],\"definitions\":[]}"), Constant.CLIENT_RESOURCE);
    }

    static HttpResponse<String> get(URI uri) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<byte[]> getBytes(URI uri) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @Path("/http-feature")
    public static class Endpoint {
        @GET
        @Path("/status")
        @Produces(MediaType.APPLICATION_JSON)
        public String status() {
            // Match Jimmer's JSON response contract without requiring a Jackson message body writer.
            return "\"ready\"";
        }

        @GET
        @Path("/failure")
        public String failure() {
            throw new Failure();
        }
    }

    @Path("/http-feature/administration")
    public static class AdministrationEndpoint {
        @GET
        @Produces(MediaType.APPLICATION_JSON)
        public String administration() {
            return "\"admin ready\"";
        }
    }

    @ClientException(family = "HTTP_TEST", code = "EXPECTED_FAILURE")
    public static class Failure extends CodeBasedRuntimeException {
        public Failure() {
            super("Expected test failure");
        }
    }
}
