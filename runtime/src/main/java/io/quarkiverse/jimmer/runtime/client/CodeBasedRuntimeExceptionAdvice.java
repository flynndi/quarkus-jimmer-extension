package io.quarkiverse.jimmer.runtime.client;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.babyfish.jimmer.error.CodeBasedRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class CodeBasedRuntimeExceptionAdvice extends CommonExceptionAdvice
        implements ExceptionMapper<CodeBasedRuntimeException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodeBasedRuntimeExceptionAdvice.class);

    @Override
    public Response toResponse(CodeBasedRuntimeException ex) {
        LOGGER.error("Auto handled HTTP Error(" + CodeBasedRuntimeException.class.getName() + ")", ex);
        return Response
                .status(config.errorTranslator().isPresent() ? config.errorTranslator().get().httpStatus() : 500)
                .entity(resultMap(ex))
                .build();
    }
}
