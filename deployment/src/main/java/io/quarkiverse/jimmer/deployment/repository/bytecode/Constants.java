package io.quarkiverse.jimmer.deployment.repository.bytecode;

import org.babyfish.jimmer.Specification;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.objectweb.asm.Type;

import io.quarkiverse.jimmer.runtime.repository.common.Sort;
import io.quarkiverse.jimmer.runtime.repository.parser.QueryPlan;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkiverse.jimmer.runtime.repository.support.QueryExecutors;

interface Constants {

    String QUERY_PLAN_DESCRIPTOR = Type.getDescriptor(QueryPlan.class);

    String IMMUTABLE_TYPE_INTERNAL_NAME = Type.getInternalName(ImmutableType.class);

    String IMMUTABLE_TYPE_DESCRIPTOR = Type.getDescriptor(ImmutableType.class);

    String DOMAIN_TYPE_FIELD = "DOMAIN_TYPE";

    String QUERY_PLAN_FACTORY_DESCRIPTOR = '(' + IMMUTABLE_TYPE_DESCRIPTOR + ')' + QUERY_PLAN_DESCRIPTOR;

    String J_SQL_CLIENT_IMPLEMENTOR_DESCRIPTOR = Type.getDescriptor(JSqlClientImplementor.class);

    String QUERY_EXECUTORS_INTERNAL_NAME = Type.getInternalName(QueryExecutors.class);

    String QUERY_EXECUTORS_METHOD_DESCRIPTOR = '(' +
            J_SQL_CLIENT_IMPLEMENTOR_DESCRIPTOR +
            IMMUTABLE_TYPE_DESCRIPTOR +
            QUERY_PLAN_DESCRIPTOR +
            Type.getDescriptor(Pagination.class) +
            Type.getDescriptor(Sort.class) +
            Type.getDescriptor(Specification.class) +
            Type.getDescriptor(Fetcher.class) +
            Type.getDescriptor(Class.class) +
            "[Ljava/lang/Object;)Ljava/lang/Object;";
}
