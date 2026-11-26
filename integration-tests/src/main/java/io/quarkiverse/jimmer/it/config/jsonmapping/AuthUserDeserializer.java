package io.quarkiverse.jimmer.it.config.jsonmapping;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

class AuthUserDeserializer extends ValueDeserializer<AuthUser> {
    @Override
    public AuthUser deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
        JsonNode jsonNode = deserializationContext.readTree(jsonParser);
        String authorities = jsonNode.path("authorities").asString();
        JsonNode passwordNode = jsonNode.path("password");
        String username = jsonNode.path("username").asString();
        String id = jsonNode.path("id").asString();
        String password = passwordNode.asString("");
        boolean enabled = jsonNode.path("enabled").asBoolean();
        boolean accountNonExpired = jsonNode.path("accountNonExpired").asBoolean();
        boolean credentialsNonExpired = jsonNode.path("credentialsNonExpired").asBoolean();
        boolean accountNonLocked = jsonNode.path("accountNonLocked").asBoolean();
        String tenantId = jsonNode.path("tenantId").asString();
        boolean deleteFlag = jsonNode.path("deleteFlag").asBoolean();
        return new AuthUser(id, password, username, authorities, accountNonExpired, accountNonLocked, credentialsNonExpired,
                enabled, tenantId, deleteFlag);
    }
}
