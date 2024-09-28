package io.quarkiverse.jimmer.deployment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.objectweb.asm.*;

class CopyMethodsFromAtoB {
    public static void main(String[] args) throws IOException {
        // 类A的字节码读取
        ClassReader classReaderA = new ClassReader("io/quarkiverse/jimmer/deployment/A");
        // 类B的字节码读取
        ClassReader classReaderB = new ClassReader("io/quarkiverse/jimmer/deployment/B");

        // 创建ClassWriter，用于生成类B的修改版
        ClassWriter classWriterB = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // 读取类B并用 ClassVisitor 进行处理，复制类结构
        classReaderB.accept(new ClassVisitor(Opcodes.ASM9, classWriterB) {
            @Override
            public void visitEnd() {
                super.visitEnd();
                // 读取类A的所有方法
                classReaderA.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                            String[] exceptions) {
                        // 排除构造方法和类中已有的方法
                        if (!name.equals("<init>") && !name.equals("<clinit>")) {
                            // 复制A类中的方法到B类
                            MethodVisitor methodVisitor = classWriterB.visitMethod(access, name, descriptor, signature,
                                    exceptions);
                            return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                                @Override
                                public void visitCode() {
                                    super.visitCode();
                                }

                                @Override
                                public void visitInsn(int opcode) {
                                    super.visitInsn(opcode);
                                }

                                @Override
                                public void visitMaxs(int maxStack, int maxLocals) {
                                    super.visitMaxs(maxStack, maxLocals);
                                }

                                @Override
                                public void visitEnd() {
                                    super.visitEnd();
                                }
                            };
                        }
                        return null;
                    }
                }, 0);
            }
        }, 0);

        // 将生成的B类的字节码写入文件
        byte[] bytecodeB = classWriterB.toByteArray();
        File outputFile = new File("io/quarkiverse/jimmer/deployment/B");
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(bytecodeB);
        }

        System.out.println("Methods copied from A to B successfully!");
    }
}
