package io.quarkiverse.jimmer.runtime.util;

import org.babyfish.jimmer.jackson.codec.JsonCodec;
import org.babyfish.jimmer.jackson.v2.ImmutableModuleV2;
import org.babyfish.jimmer.jackson.v2.JsonCodecV2;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jimmer's {@link JsonCodecV2} wraps a supplied {@link ObjectMapper} as-is, unlike the legacy
 * {@code ValueSerializer(ObjectMapper)} constructor it replaces, which used to copy the mapper and
 * register the immutable module automatically. Go through this helper instead of {@code new JsonCodecV2(mapper)}
 * directly so the caller's mapper is never mutated and immutable entities keep serializing correctly
 * even when a plain, unconfigured {@link ObjectMapper} is supplied.
 */
public final class JimmerJsonCodecs {

    private JimmerJsonCodecs() {
    }

    public static JsonCodec<?> toJsonCodecV2(@Nullable ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new ImmutableModuleV2());
        return new JsonCodecV2(mapper);
    }
}
