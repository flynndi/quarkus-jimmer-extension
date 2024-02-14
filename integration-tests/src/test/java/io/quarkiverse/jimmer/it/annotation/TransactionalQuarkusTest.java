package io.quarkiverse.jimmer.it.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.inject.Stereotype;
import jakarta.transaction.Transactional;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Stereotype
@Transactional(rollbackOn = Exception.class)
public @interface TransactionalQuarkusTest {
}
