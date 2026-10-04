package io.quarkiverse.jimmer.deployment.client;

import java.util.Set;

import jakarta.ws.rs.Priorities;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

final class JimmerRestProcessor {

    @BuildStep
    void validateCapability(JimmerBuildTimeConfig config, Capabilities capabilities,
            BuildProducer<ValidationErrorBuildItem> errors) {
        // Arc consumes this item even when REST is absent and no processor consumes ExceptionMapperBuildItem.
        if (enabled(config) && !supportsJsonErrors(capabilities)) {
            errors.produce(new ValidationErrorBuildItem(new ConfigurationException(
                    "Jimmer error translation requires the quarkus-rest-jackson extension (Quarkus REST with Jackson JSON); "
                            + "add it or set quarkus.jimmer.error-translator.disabled=true",
                    Set.of("quarkus.jimmer.error-translator.disabled"))));
        }
    }

    @BuildStep
    void registerExceptionMappers(JimmerBuildTimeConfig config, Capabilities capabilities,
            BuildProducer<ExceptionMapperBuildItem> mappers) {
        if (!enabled(config) || !supportsJsonErrors(capabilities)) {
            return;
        }
        mappers.produce(new ExceptionMapperBuildItem(
                "io.quarkiverse.jimmer.runtime.client.CodeBasedExceptionAdvice",
                "org.babyfish.jimmer.error.CodeBasedException", Priorities.USER + 1, true));
        mappers.produce(new ExceptionMapperBuildItem(
                "io.quarkiverse.jimmer.runtime.client.CodeBasedRuntimeExceptionAdvice",
                "org.babyfish.jimmer.error.CodeBasedRuntimeException", Priorities.USER + 1, true));
    }

    private static boolean enabled(JimmerBuildTimeConfig config) {
        return config.enable() && config.errorTranslator().isPresent() && !config.errorTranslator().get().disabled();
    }

    private static boolean supportsJsonErrors(Capabilities capabilities) {
        return capabilities.isPresent(Capability.REST) && capabilities.isPresent(Capability.REST_JACKSON);
    }
}
