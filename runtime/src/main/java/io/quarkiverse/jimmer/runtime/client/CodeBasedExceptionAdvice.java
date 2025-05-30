package io.quarkiverse.jimmer.runtime.client;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.babyfish.jimmer.error.CodeBasedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;

@Provider
public class CodeBasedExceptionAdvice extends CommonExceptionAdvice implements ExceptionMapper<CodeBasedException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeBasedExceptionAdvice.class);

    public CodeBasedExceptionAdvice(JimmerBuildTimeConfig buildTimeConfig) {
        super(buildTimeConfig);
    }

    @Override
    public Response toResponse(CodeBasedException ex) {
        LOGGER.error("Auto handled HTTP Error(" + CodeBasedException.class.getName() + ")", ex);
        return Response
                .status(buildTimeConfig.errorTranslator().isPresent() ? buildTimeConfig.errorTranslator().get().httpStatus()
                        : 500)
                .entity(resultMap(ex))
                .build();
    }
}
