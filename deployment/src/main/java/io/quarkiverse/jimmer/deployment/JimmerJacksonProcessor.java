package io.quarkiverse.jimmer.deployment;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.jackson.spi.ClassPathJacksonModuleBuildItem;

public class JimmerJacksonProcessor {

    private static final String JIMMER_JACKSON_MODULE = "org.babyfish.jimmer.jackson.ImmutableModule";

    @BuildStep
    void registerJimmerJacksonModule(BuildProducer<ClassPathJacksonModuleBuildItem> classPathJacksonModules) {
        if (!QuarkusClassLoader.isClassPresentAtRuntime(JIMMER_JACKSON_MODULE)) {
            return;
        }
        classPathJacksonModules.produce(new ClassPathJacksonModuleBuildItem(JIMMER_JACKSON_MODULE));
    }
}
