package io.quarkiverse.jimmer.runtime.meta;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.babyfish.jimmer.sql.meta.MetaStringResolver;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.smallrye.common.expression.Expression;
import io.smallrye.common.expression.ResolveContext;

public class QuarkusMetaStringResolver implements MetaStringResolver {

    @Override
    @Nullable
    public String resolve(@NotNull String propertyValue) {
        String value = propertyValue.stripLeading();
        if (!value.isEmpty() && isConfigValue(value)) {
            value = resolvePropertyExpression(adjustExpressionSyntax(value));
        }
        return value;
    }

    private static String resolvePropertyExpression(String expr) {
        final ClassLoader cl = QuarkusMetaStringResolver.class.getClassLoader();
        final Config config = ConfigProviderResolver.instance().getConfig(cl);
        final Expression expression = Expression.compile(expr, Expression.Flag.LENIENT_SYNTAX, Expression.Flag.NO_TRIM);
        return expression.evaluate(new BiConsumer<ResolveContext<RuntimeException>, StringBuilder>() {
            @Override
            public void accept(ResolveContext<RuntimeException> resolveContext, StringBuilder stringBuilder) {
                final Optional<String> resolve = config.getOptionalValue(resolveContext.getKey(), String.class);
                if (resolve.isPresent()) {
                    stringBuilder.append(resolve.get());
                } else if (resolveContext.hasDefault()) {
                    resolveContext.expandDefault();
                } else {
                    throw new NoSuchElementException(String.format("Could not expand value %s in property %s",
                            resolveContext.getKey(), expr));
                }
            }
        });
    }

    private static String adjustExpressionSyntax(String val) {
        if (isSimpleConfigValue(val)) {
            return '$' + val;
        }
        return val;
    }

    public static boolean isConfigValue(String val) {
        return isSimpleConfigValue(val) || isConfigExpression(val);
    }

    private static boolean isSimpleConfigValue(String val) {
        val = val.trim();
        return val.startsWith("{") && val.endsWith("}");
    }

    private static boolean isConfigExpression(String val) {
        if (val == null) {
            return false;
        }
        int exprStart = val.indexOf("${");
        int exprEnd = -1;
        if (exprStart >= 0) {
            exprEnd = val.indexOf('}', exprStart + 2);
        }
        return exprEnd > 0;
    }
}
