package io.quarkiverse.jimmer.deployment.bytecode;

import java.lang.reflect.Method;

import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.repository.support.KRepositoryImpl;

class KotlinClassCodeWriter extends ClassCodeWriter {

    public KotlinClassCodeWriter(RepositoryMetadata metadata) {
        super(metadata, KSqlClient.class, KRepositoryImpl.class);
    }

    @Override
    protected MethodCodeWriter createMethodCodeWriter(Method method, String id) {
        return new KotlinMethodCodeWriter(this, method, id);
    }
}
