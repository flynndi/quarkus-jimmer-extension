package io.quarkiverse.jimmer.it.config.jsonmapping;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.Customizer;

import io.quarkiverse.jimmer.runtime.util.JimmerJsonCodecs;
import io.quarkus.arc.Unremovable;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

@ApplicationScoped
@Unremovable
public class SerializationCustomizer implements Customizer {

    private final JsonMapper objectMapper;

    public SerializationCustomizer(JsonMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void customize(JSqlClient.Builder builder) {
        JsonMapper userMapper = objectMapper.rebuild()
                .addMixIn(AuthUser.class, AuthUserMixin.class)
                .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .build();
        builder.setSerializedTypeJsonCodec(AuthUser.class, JimmerJsonCodecs.toJsonCodecV3(userMapper));
    }
}
