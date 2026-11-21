package io.quarkiverse.jimmer.deployment.repository.bytecode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.objectweb.asm.*;

import io.quarkiverse.jimmer.deployment.repository.RepositoryMetadata;
import io.quarkiverse.jimmer.runtime.repository.parser.Context;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Unremovable;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.runtime.util.HashUtil;

abstract class ClassCodeWriter implements Constants {

    final RepositoryMetadata metadata;

    private final String interfaceInternalName;

    private final String implInternalName;

    private final String superInternalName;

    private final String sqlClientDescriptor;

    protected final List<MethodCodeWriter> methodCodeWriters;

    protected final List<MethodCodeWriter> autoGenMethodCodeWriters;

    private ClassWriter cw;

    final Context ctx = new Context();

    protected ClassCodeWriter(RepositoryMetadata metadata, Class<?> sqlClientType, Class<?> superType) {
        this.metadata = metadata;
        this.interfaceInternalName = Type.getInternalName(metadata.getRepositoryInterface());
        this.implInternalName = interfaceInternalName + "_" + HashUtil.sha1(interfaceInternalName) + "Impl";
        this.superInternalName = Type.getInternalName(superType);
        this.sqlClientDescriptor = Type.getDescriptor(sqlClientType);
        Class<?> repositoryInterface = metadata.getRepositoryInterface();
        List<MethodCodeWriter> list = new ArrayList<>();
        Map<String, Integer> methodNameCountMap = new HashMap<>();
        for (Method method : repositoryInterface.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) &&
                    !method.getDeclaringClass().isAssignableFrom(superType)) {
                Integer count = methodNameCountMap.get(method.getName());
                if (count == null) {
                    list.add(createMethodCodeWriter(method, method.getName()));
                    methodNameCountMap.put(method.getName(), 1);
                } else {
                    ++count;
                    list.add(createMethodCodeWriter(method, method.getName() + ":" + count));
                    methodNameCountMap.put(method.getName(), count);
                }
            }
        }
        this.methodCodeWriters = list;
        this.autoGenMethodCodeWriters = list
                .stream()
                .filter(it -> !it.method.isDefault() && it.getDefaultImplMethod() == null)
                .collect(Collectors.toList());
    }

    public RepositoryMetadata getMetadata() {
        return metadata;
    }

    public String getImplInternalName() {
        return implInternalName;
    }

    public ClassWriter getClassVisitor() {
        return cw;
    }

    public byte[] write() {
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                implInternalName,
                null,
                superInternalName,
                new String[] { interfaceInternalName });
        AnnotationVisitor av = cw.visitAnnotation(Type.getDescriptor(Singleton.class), true);
        av.visitEnd();
        av = cw.visitAnnotation(Type.getDescriptor(Unremovable.class), true);
        av.visitEnd();
        if (!metadata.getDataSourceName().equals(DataSourceUtil.DEFAULT_DATASOURCE_NAME)) {
            av = cw.visitAnnotation(Type.getDescriptor(DataSource.class), true);
            av.visit("value", metadata.getDataSourceName());
            av.visitEnd();
        }
        writeInit();
        if (!autoGenMethodCodeWriters.isEmpty()) {
            writeStaticFields();
            writeClinit();
        }
        for (MethodCodeWriter writer : methodCodeWriters) {
            writer.write();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void writeInit() {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                '(' + sqlClientDescriptor + ")V",
                null,
                null);
        // Add @Inject annotation
        AnnotationVisitor av = mv.visitAnnotation(Type.getDescriptor(jakarta.inject.Inject.class), true);
        av.visitEnd();
        if (!metadata.getDataSourceName().equals(DataSourceUtil.DEFAULT_DATASOURCE_NAME)) {
            av = mv.visitParameterAnnotation(0, Type.getDescriptor(DataSource.class), true);
            av.visit("value", metadata.getDataSourceName());
            av.visitEnd();
        }
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitLdcInsn(Type.getType(metadata.getDomainType()));
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                superInternalName,
                "<init>",
                "(" + sqlClientDescriptor + "Ljava/lang/Class;)V",
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // No-args constructor
        mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC,
                "<init>",
                "()V",
                null,
                null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                superInternalName,
                "<init>",
                '(' + sqlClientDescriptor + ")V",
                false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeStaticFields() {
        int access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
        cw.visitField(access, DOMAIN_TYPE_FIELD, IMMUTABLE_TYPE_DESCRIPTOR, null, null).visitEnd();
        for (MethodCodeWriter writer : autoGenMethodCodeWriters) {
            cw.visitField(access, writer.queryPlanFieldName(), QUERY_PLAN_DESCRIPTOR, null, null).visitEnd();
        }
    }

    private void writeClinit() {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_STATIC,
                "<clinit>",
                "()V",
                null,
                null);
        mv.visitCode();

        mv.visitLdcInsn(Type.getType(metadata.getDomainType()));
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                IMMUTABLE_TYPE_INTERNAL_NAME,
                "get",
                "(Ljava/lang/Class;)" + IMMUTABLE_TYPE_DESCRIPTOR,
                true);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, implInternalName, DOMAIN_TYPE_FIELD, IMMUTABLE_TYPE_DESCRIPTOR);

        for (MethodCodeWriter writer : autoGenMethodCodeWriters) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, implInternalName, DOMAIN_TYPE_FIELD, IMMUTABLE_TYPE_DESCRIPTOR);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, implInternalName, writer.queryPlanFactoryName(),
                    QUERY_PLAN_FACTORY_DESCRIPTOR, false);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, implInternalName, writer.queryPlanFieldName(), QUERY_PLAN_DESCRIPTOR);
        }
        mv.visitInsn(Opcodes.RETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    protected abstract MethodCodeWriter createMethodCodeWriter(Method method, String id);
}
