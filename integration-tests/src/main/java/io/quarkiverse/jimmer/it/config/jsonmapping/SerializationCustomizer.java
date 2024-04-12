package io.quarkiverse.jimmer.it.config.jsonmapping;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.Customizer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class SerializationCustomizer implements Customizer {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void customize(JSqlClient.Builder builder) {
        builder
                .setSerializedTypeObjectMapper(AuthUser.class,
                        objectMapper.addMixIn(AuthUser.class, AuthUserMixin.class)
                                .enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION));
    }
}
