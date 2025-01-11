package io.quarkiverse.jimmer.deployment.bytecode;

import java.lang.reflect.Method;

import org.babyfish.jimmer.Specification;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.objectweb.asm.Type;

import io.quarkiverse.jimmer.runtime.repository.common.Sort;
import io.quarkiverse.jimmer.runtime.repository.parser.Context;
import io.quarkiverse.jimmer.runtime.repository.parser.QueryMethod;
import io.quarkiverse.jimmer.runtime.repository.support.KRepositoryImpl;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkiverse.jimmer.runtime.repository.support.QueryExecutors;

interface Constants {

    String CONTEXT_INTERNAL_NAME = Type.getInternalName(Context.class);

    String CONTEXT_DESCRIPTOR = Type.getDescriptor(Context.class);

    String QUERY_METHOD_INTERNAL_NAME = Type.getInternalName(QueryMethod.class);

    String QUERY_METHOD_DESCRIPTOR = Type.getDescriptor(QueryMethod.class);

    String IMMUTABLE_TYPE_INTERNAL_NAME = Type.getInternalName(ImmutableType.class);

    String IMMUTABLE_TYPE_DESCRIPTOR = Type.getDescriptor(ImmutableType.class);

    String METHOD_DESCRIPTOR = Type.getDescriptor(Method.class);

    String K_SQL_CLIENT_INTERNAL_NAME = Type.getInternalName(KSqlClient.class);

    String K_SQL_CLIENT_DESCRIPTOR = Type.getDescriptor(KSqlClient.class);

    String J_SQL_CLIENT_DESCRIPTOR = Type.getDescriptor(JSqlClient.class);

    String J_SQL_CLIENT_IMPLEMENTOR_INTERNAL_NAME = Type.getInternalName(JSqlClientImplementor.class);

    String J_SQL_CLIENT_IMPLEMENTOR_DESCRIPTOR = Type.getDescriptor(JSqlClientImplementor.class);

    String K_REPOSITORY_IMPL = Type.getInternalName(KRepositoryImpl.class);

    String QUERY_EXECUTORS_INTERNAL_NAME = Type.getInternalName(QueryExecutors.class);

    String QUERY_EXECUTORS_METHOD_DESCRIPTOR = '(' +
            J_SQL_CLIENT_IMPLEMENTOR_DESCRIPTOR +
            IMMUTABLE_TYPE_DESCRIPTOR +
            QUERY_METHOD_DESCRIPTOR +
            Type.getDescriptor(Pagination.class) +
            Type.getDescriptor(Sort.class) +
            Type.getDescriptor(Specification.class) +
            Type.getDescriptor(Fetcher.class) +
            Type.getDescriptor(Class.class) +
            "[Ljava/lang/Object;)Ljava/lang/Object;";
}
