package io.quarkiverse.jimmer.it.config;

import jakarta.inject.Singleton;

import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class RegisterCustomModuleCustomizer implements JsonMapperBuilderCustomizer {

    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
}
