package io.quarkiverse.jimmer.deployment.repository.bytecode;

import static org.objectweb.asm.Type.getType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.ast.LikeMode;
import org.babyfish.jimmer.sql.ast.query.OrderMode;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import io.quarkiverse.jimmer.runtime.repository.parser.AndPredicate;
import io.quarkiverse.jimmer.runtime.repository.parser.OrPredicate;
import io.quarkiverse.jimmer.runtime.repository.parser.Path;
import io.quarkiverse.jimmer.runtime.repository.parser.Predicate;
import io.quarkiverse.jimmer.runtime.repository.parser.PropPredicate;
import io.quarkiverse.jimmer.runtime.repository.parser.Query;
import io.quarkiverse.jimmer.runtime.repository.parser.QueryPlan;

final class QueryPlanCodeWriter {

    private final GeneratorAdapter code;

    private QueryPlanCodeWriter(ClassWriter cw, String methodName) {
        code = new GeneratorAdapter(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                method(methodName, QueryPlan.class, ImmutableType.class), null, null, cw);
    }

    static void write(ClassWriter cw, String methodName, QueryPlan plan) {
        new QueryPlanCodeWriter(cw, methodName).writePlan(plan);
    }

    private void writePlan(QueryPlan plan) {
        code.visitCode();
        allocate(QueryPlan.class);
        writeQuery(plan.query());
        writeEnum(plan.returnShape());
        code.push(plan.methodDescription());
        writeList(plan.parameters(), parameter -> {
            allocate(QueryPlan.Parameter.class);
            code.push(parameter.name());
            code.push(parameter.dynamic());
            initialize(QueryPlan.Parameter.class, String.class, boolean.class);
        });
        initialize(QueryPlan.class, Query.class, QueryPlan.ReturnShape.class, String.class, List.class);
        code.returnValue();
        code.endMethod();
    }

    private void writeQuery(Query query) {
        allocate(Query.class);
        writeEnum(query.getAction());
        code.push(query.getLimit());
        code.push(query.isDistinct());
        writePath(query.getSelectedPath());
        writePredicate(query.getPredicate());
        writeList(query.getOrders(), order -> {
            allocate(Query.Order.class);
            writePath(order.getPath());
            writeEnum(order.getOrderMode());
            initialize(Query.Order.class, Path.class, OrderMode.class);
        });
        initialize(Query.class, Query.Action.class, int.class, boolean.class, Path.class, Predicate.class, List.class);
    }

    private void writePredicate(Predicate predicate) {
        if (predicate == null) {
            code.visitInsn(Opcodes.ACONST_NULL);
        } else if (predicate instanceof AndPredicate and) {
            writeList(and.getPredicates(), this::writePredicate);
            code.invokeStatic(getType(AndPredicate.class), method("of", Predicate.class, List.class));
        } else if (predicate instanceof OrPredicate or) {
            writeList(or.getPredicates(), this::writePredicate);
            code.invokeStatic(getType(OrPredicate.class), method("of", Predicate.class, List.class));
        } else if (predicate instanceof PropPredicate.Resolved prop) {
            allocate(PropPredicate.Resolved.class);
            writePath(prop.getPath());
            writeEnum(prop.getOp());
            code.push(prop.isInsensitive());
            writeEnum(prop.getLikeMode());
            code.push(prop.getParamIndex());
            code.push(prop.getLogicParamIndex());
            code.push(prop.getParamIndex2());
            code.push(prop.getLogicParamIndex2());
            initialize(PropPredicate.Resolved.class, Path.class, PropPredicate.Op.class, boolean.class, LikeMode.class,
                    int.class, int.class, int.class, int.class);
        } else {
            throw new IllegalArgumentException("Unsupported query predicate: " + predicate);
        }
    }

    private void writePath(Path path) {
        if (path == null) {
            code.visitInsn(Opcodes.ACONST_NULL);
            return;
        }
        code.loadArg(0);
        writeArray(String.class, path.getProps(), prop -> code.push(prop.getName()));
        code.invokeStatic(getType(Path.class), method("resolved", Path.class, ImmutableType.class, String[].class));
    }

    private void writeEnum(Enum<?> value) {
        Type enumType = getType(value.getDeclaringClass());
        code.getStatic(enumType, value.name(), enumType);
    }

    private <T> void writeList(List<T> values, Consumer<T> writeValue) {
        Method factory;
        if (values.isEmpty()) {
            factory = method("of", List.class);
        } else {
            writeArray(Object.class, values, writeValue);
            factory = method("of", List.class, Object[].class);
        }
        code.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(List.class), factory.getName(),
                factory.getDescriptor(), true);
    }

    private <T> void writeArray(Class<?> elementType, List<T> values, Consumer<T> writeValue) {
        Type type = getType(elementType);
        code.push(values.size());
        code.newArray(type);
        for (int i = 0; i < values.size(); i++) {
            code.dup();
            code.push(i);
            writeValue.accept(values.get(i));
            code.arrayStore(type);
        }
    }

    private void allocate(Class<?> type) {
        code.newInstance(getType(type));
        code.dup();
    }

    private void initialize(Class<?> type, Class<?>... parameters) {
        code.invokeConstructor(getType(type), method("<init>", void.class, parameters));
    }

    private static Method method(String name, Class<?> returnType, Class<?>... parameters) {
        return new Method(name, getType(returnType), Arrays.stream(parameters).map(Type::getType).toArray(Type[]::new));
    }
}
