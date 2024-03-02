package io.quarkiverse.jimmer.runtime.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.error.CodeBasedException;
import org.babyfish.jimmer.error.CodeBasedRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;

public abstract class CommonExceptionAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonExceptionAdvice.class);

    JimmerBuildTimeConfig config;

    public CommonExceptionAdvice(JimmerBuildTimeConfig config) {
        this.config = config;
        if (config.errorTranslator().isPresent() && config.errorTranslator().get().debugInfoSupported()) {
            notice();
        }
    }

    protected void notice() {
        String builder = "\n" + "------------------------------------------------\n" +
                "|                                              |\n" +
                "|`jimmer.error-translator.debug-info-supported`|\n" +
                "|has been turned on, this is dangerous, please |\n" +
                "|make sure the current environment is          |\n" +
                "|NOT PRODUCTION!                               |\n" +
                "|                                              |\n" +
                "------------------------------------------------\n";
        LOGGER.info(builder);
    }

    protected Map<String, Object> resultMap(CodeBasedException ex) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("family", ex.getFamily());
        resultMap.put("code", ex.getCode());
        resultMap.putAll(ex.getFields());
        if (config.errorTranslator().isPresent()) {
            if (config.errorTranslator().get().debugInfoSupported()) {
                resultMap.put("debugInfo", debugInfoMap(ex));
            }
        }
        return resultMap;
    }

    protected Map<String, Object> resultMap(CodeBasedRuntimeException ex) {
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("family", ex.getFamily());
        resultMap.put("code", ex.getCode());
        resultMap.putAll(ex.getFields());
        if (config.errorTranslator().isPresent()) {
            if (config.errorTranslator().get().debugInfoSupported()) {
                resultMap.put("debugInfo", debugInfoMap(ex));
            }
        }
        return resultMap;
    }

    protected Map<String, Object> debugInfoMap(Throwable ex) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", ex.getMessage());
        StackTraceElement[] elements = ex.getStackTrace();
        int size = Math.min(elements.length, config.errorTranslator().get().debugInfoMaxStackTraceCount);
        List<String> stackFrames = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stackFrames.add(elements[i].toString());
        }
        map.put("stackFrames", stackFrames);
        if (ex.getCause() != null) {
            map.put("causeBy", debugInfoMap(ex.getCause()));
        }
        return map;
    }
}
