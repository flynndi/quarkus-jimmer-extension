package io.quarkiverse.jimmer.deployment.bytecode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class KotlinMethodCodeWriter extends MethodCodeWriter {

    protected KotlinMethodCodeWriter(ClassCodeWriter parent, Method method, String id) {
        super(parent, method, id);
    }

    @Override
    protected void visitLoadJSqlClient() {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, K_REPOSITORY_IMPL, "getSql", "()" + K_SQL_CLIENT_DESCRIPTOR, false);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, K_SQL_CLIENT_INTERNAL_NAME, "getJavaClient",
                "()" + J_SQL_CLIENT_IMPLEMENTOR_DESCRIPTOR, true);
    }

    @Override
    public void write() {
        if (method.isDefault()) {
            return;
        }
        Method defaultMethod = getDefaultImplMethod();
        if (defaultMethod != null) {
            writeDefaultInvocation(defaultMethod);
            return;
        }
        super.write();
    }

    private void writeDefaultInvocation(Method defaultMethod) {
        mv = parent.getClassVisitor().visitMethod(Opcodes.ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null,
                null);
        mv.visitCode();
        VarLoader loader = new VarLoader(0);
        for (Class<?> type : defaultMethod.getParameterTypes()) {
            loader.load(type);
        }
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(defaultMethod.getDeclaringClass()),
                method.getName(),
                Type.getMethodDescriptor(defaultMethod),
                false);
        visitReturn(method.getReturnType());
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    @Override
    protected Method onGetDefaultImplMethod() {
        Class<?> repositoryInterface = parent.getMetadata().getRepositoryInterface();
        Class<?> defaultImpl = null;
        for (Class<?> nestedClass : repositoryInterface.getClasses()) {
            if (nestedClass.getSimpleName().equals("DefaultImpls")) {
                defaultImpl = nestedClass;
                break;
            }
        }
        if (defaultImpl == null) {
            return null;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        Class<?>[] newParameterTypes = new Class[parameterTypes.length + 1];
        System.arraycopy(parameterTypes, 0, newParameterTypes, 1, parameterTypes.length);
        newParameterTypes[0] = repositoryInterface;
        Method defaultImplMethod;
        try {
            defaultImplMethod = defaultImpl.getMethod(method.getName(), newParameterTypes);
        } catch (NoSuchMethodException ex) {
            return null;
        }
        if (Modifier.isStatic(defaultImplMethod.getModifiers())) {
            return defaultImplMethod;
        }
        return null;
    }
}
