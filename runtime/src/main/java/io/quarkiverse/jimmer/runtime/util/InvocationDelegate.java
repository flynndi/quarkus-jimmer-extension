package io.quarkiverse.jimmer.runtime.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class InvocationDelegate implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(args);
    }
}
