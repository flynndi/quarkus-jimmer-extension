package io.quarkiverse.jimmer.deployment.jackson;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Jackson2Present;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassConditionBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;

final class JimmerJackson2Processor {

    @BuildStep
    void guardJackson2Reflection(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassConditionBuildItem> conditions) {
        // Hierarchy scans of Jimmer's codec interfaces can register their optional Jackson 2 implementations.
        for (var type : combinedIndex.getIndex().getClassesInPackage("org.babyfish.jimmer.jackson.v2")) {
            conditions.produce(new ReflectiveClassConditionBuildItem(type.name().toString(),
                    "com.fasterxml.jackson.databind.ObjectMapper"));
        }
    }

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class, onlyIfNot = Jackson2Present.class)
    BytecodeTransformerBuildItem pruneUnusedJackson2BinLogHelper() {
        // BinLogImpl in Jimmer 0.12.5 retains an unused private helper whose signature GraalVM resolves
        // when enumerating the methods of the registered BinLog implementation.
        return new BytecodeTransformerBuildItem("org.babyfish.jimmer.sql.event.binlog.impl.BinLogImpl",
                (name, visitor) -> new ClassVisitor(Opcodes.ASM9, visitor) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                            String[] exceptions) {
                        int privateStatic = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
                        if ((access & privateStatic) == privateStatic && name.equals("nodeOf") && descriptor.equals(
                                "(Lcom/fasterxml/jackson/databind/JsonNode;Ljava/lang/String;)Lcom/fasterxml/jackson/databind/JsonNode;")) {
                            return null;
                        }
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }
                });
    }
}
