package io.quarkiverse.jimmer.it.config.jsonmapping;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.Customizer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class SerializationCustomizer implements Customizer {

    private final ObjectMapper objectMapper;

    public SerializationCustomizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void customize(JSqlClient.Builder builder) {
        builder
                .setSerializedTypeObjectMapper(AuthUser.class,
                        objectMapper.addMixIn(AuthUser.class, AuthUserMixin.class)
                                .enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION));
    }
}
