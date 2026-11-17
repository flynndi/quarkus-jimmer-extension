package io.quarkiverse.jimmer.deployment.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.jimmer.runtime.util.Constant;

/** Adapts the compile-time client schema before Jimmer resolves its runtime types. */
final class JimmerClientMetadata {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REST_RESPONSE = "org.jboss.resteasy.reactive.RestResponse";

    private JimmerClientMetadata() {
    }

    static List<ObjectNode> read(ClassLoader loader) throws IOException {
        List<ObjectNode> schemas = new ArrayList<>();
        var resources = loader.getResources(Constant.CLIENT_RESOURCE);
        while (resources.hasMoreElements()) {
            var resource = resources.nextElement();
            try (var input = resource.openStream()) {
                schemas.add((ObjectNode) MAPPER.readTree(input));
            } catch (IOException ex) {
                throw new IOException("Cannot read Jimmer client metadata from " + resource, ex);
            }
        }
        return schemas;
    }

    static boolean unwrapRestResponses(ObjectNode schema) {
        boolean changed = false;
        for (JsonNode service : schema.path("services")) {
            for (JsonNode operation : service.path("operations")) {
                JsonNode returnType = operation.get("returnType");
                while (returnType != null && REST_RESPONSE.equals(returnType.path("typeName").asText())) {
                    JsonNode arguments = returnType.path("arguments");
                    if (arguments.size() != 1 || !arguments.get(0).isObject()) {
                        throw new IllegalArgumentException("RestResponse must declare its response body type: "
                                + service.path("typeName").asText() + '.' + operation.path("name").asText());
                    }
                    ObjectNode bodyType = ((ObjectNode) arguments.get(0)).deepCopy();
                    if (returnType.path("nullable").asBoolean()) {
                        bodyType.put("nullable", true);
                    }
                    returnType = bodyType;
                    if ("java.lang.Void".equals(bodyType.path("typeName").asText())
                            || "void".equals(bodyType.path("typeName").asText())) {
                        ((ObjectNode) operation).remove("returnType");
                        returnType = null;
                    } else {
                        ((ObjectNode) operation).set("returnType", bodyType);
                    }
                    changed = true;
                }
            }
        }
        return changed;
    }

    static byte[] write(ObjectNode schema) throws IOException {
        return MAPPER.writeValueAsBytes(schema);
    }
}
