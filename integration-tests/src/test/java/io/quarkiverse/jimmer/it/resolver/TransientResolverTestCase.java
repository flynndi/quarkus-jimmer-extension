package io.quarkiverse.jimmer.it.resolver;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import org.babyfish.jimmer.lang.Ref;
import org.babyfish.jimmer.sql.TransientResolver;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.IntegrationTestsProfile;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(IntegrationTestsProfile.class)
public class TransientResolverTestCase {

    @Test
    void testTransientResolver() {
        InstanceHandle<Object> instance = Arc.container().instance("testTransientResolver");
        Assertions.assertInstanceOf(TransientResolver.class, instance.get());
        Set<Annotation> qualifiers = instance.getBean().getQualifiers();
        Optional<Annotation> optional = qualifiers.stream()
                .filter(x -> DotName.createSimple(Named.class).toString().equals(x.annotationType().getName())).findFirst();
        Assertions.assertTrue(optional.isPresent());
        Assertions.assertEquals(optional.get().annotationType().getName(), DotName.createSimple(Named.class).toString());
    }

    @ApplicationScoped
    @Unremovable
    static class TestTransientResolver implements TransientResolver<Long, BigDecimal> {

        @Override
        public Map<Long, BigDecimal> resolve(Collection<Long> longs) {
            return Map.of();
        }

        @Override
        public BigDecimal getDefaultValue() {
            return TransientResolver.super.getDefaultValue();
        }

        @Override
        public Ref<SortedMap<String, Object>> getParameterMapRef() {
            return TransientResolver.super.getParameterMapRef();
        }
    }
}
