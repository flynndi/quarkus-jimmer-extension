package io.quarkiverse.jimmer.runtime.util;

import org.babyfish.jimmer.jackson.codec.JsonCodec;
import org.babyfish.jimmer.jackson.v3.ImmutableModuleV3;
import org.babyfish.jimmer.jackson.v3.JsonCodecV3;
import org.jetbrains.annotations.Nullable;

import tools.jackson.databind.json.JsonMapper;

public final class JimmerJsonCodecs {

    private JimmerJsonCodecs() {
    }

    /** Reuses a configured mapper and supplies the immutable module for standalone cache binders. */
    public static JsonCodec<?> toJsonCodecV3(@Nullable JsonMapper mapper) {
        if (mapper != null && mapper.registeredModules().stream()
                .anyMatch(module -> ImmutableModuleV3.MODULE_ID.equals(module.getRegistrationId()))) {
            return new JsonCodecV3(mapper);
        }
        JsonMapper.Builder builder = mapper != null ? mapper.rebuild() : JsonMapper.builder();
        return new JsonCodecV3(builder.addModule(new ImmutableModuleV3()).build());
    }
}
