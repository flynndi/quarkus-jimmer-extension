package io.quarkiverse.jimmer.runtime.client.ts;

import java.io.ByteArrayOutputStream;

import org.babyfish.jimmer.client.generator.ts.NullRenderMode;
import org.babyfish.jimmer.client.generator.ts.TypeScriptContext;
import org.babyfish.jimmer.client.runtime.Metadata;

import io.quarkiverse.jimmer.runtime.cfg.JimmerClientConfig;

/** Generates a TypeScript client archive from resolved API metadata without an HTTP request. */
public final class TypeScriptGenerator {

    private TypeScriptGenerator() {
    }

    public static byte[] generate(Metadata metadata, JimmerClientConfig.TypeScript config) {
        TypeScriptContext context = new TypeScriptContext(metadata, config.indent(), config.mutable(), config.apiName(),
                switch (config.nullRenderMode()) {
                    case UNDEFINED -> NullRenderMode.UNDEFINED;
                    case NULL_OR_UNDEFINED -> NullRenderMode.NULL_OR_UNDEFINED;
                }, config.isEnumTsStyle());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        context.renderAll(output);
        return output.toByteArray();
    }
}
