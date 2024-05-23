package io.quarkiverse.jimmer.runtime.graal;

import org.graalvm.nativeimage.hosted.Feature;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;

public final class JimmerFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        ByteBuddyAgent.install();
        String className = "org.babyfish.jimmer.sql.dialect.PostgresDialect";
        Class<?> postgresDialectClass = access.findClassByName(className);

        new ByteBuddy()
                .redefine(postgresDialectClass)
                .method(ElementMatchers.named("getJsonBaseType"))
                .intercept(FixedValue.nullValue())
                .method(ElementMatchers.named("jsonToBaseValue"))
                .intercept(FixedValue.nullValue())
                .method(ElementMatchers.named("baseValueToJson"))
                .intercept(FixedValue.nullValue())
                .method(ElementMatchers.named("unknownReader"))
                .intercept(FixedValue.nullValue())
                .make()
                .load(access.getApplicationClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
    }
}
