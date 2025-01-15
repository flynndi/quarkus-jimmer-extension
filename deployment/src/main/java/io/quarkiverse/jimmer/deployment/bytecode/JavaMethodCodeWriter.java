package io.quarkiverse.jimmer.deployment.bytecode;

import java.lang.reflect.Method;

import org.objectweb.asm.Opcodes;

class JavaMethodCodeWriter extends MethodCodeWriter {

    protected JavaMethodCodeWriter(ClassCodeWriter parent, Method method, String id) {
        super(parent, method, id);
    }

    @Override
    protected void visitLoadJSqlClient() {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, parent.getImplInternalName(), "sqlClient", J_SQL_CLIENT_IMPLEMENTOR_DESCRIPTOR);
    }
}
