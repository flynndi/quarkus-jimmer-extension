package io.quarkiverse.jimmer.runtime.repository.support;

import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;

public class JimmerRepositoryBeanCreator implements BeanCreator<Object> {

    @Override
    public Object create(SyntheticCreationalContext<Object> context) {
        System.out.println("context = " + context);
        System.out.println("context.getParams() = " + context.getParams());
        System.out.println("context.getInterceptionProxy() = " + context.getInterceptionProxy());
        return BeanCreator.super.create(context);
    }
}
