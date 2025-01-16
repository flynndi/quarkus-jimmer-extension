package io.quarkiverse.jimmer.deployment.bytecode;

import java.lang.reflect.Method;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.runtime.repository.support.JRepositoryImpl;

class JavaClassCodeWriter extends ClassCodeWriter {

    public JavaClassCodeWriter(RepositoryMetadata metadata) {
        super(metadata, JSqlClient.class, JRepositoryImpl.class);
    }

    @Override
    protected MethodCodeWriter createMethodCodeWriter(Method method, String id) {
        return new JavaMethodCodeWriter(this, method, id);
    }
}
