package io.quarkiverse.jimmer.deployment.bytecode;

import org.jetbrains.annotations.NotNull;

import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.KRepository;
import io.quarkus.gizmo.ClassOutput;

public class JimmerRepositoryFactory {

    private final RepositoryMetadata metadata;

    private final ClassOutput classOutput;

    public JimmerRepositoryFactory(RepositoryMetadata metadata, ClassOutput classOutput) {
        this.metadata = metadata;
        this.classOutput = classOutput;
    }

    @NotNull
    public Class<?> getTargetRepositoryClass() {
        Class<?> repositoryInterface = this.metadata.getRepositoryInterface();
        boolean jRepository = JRepository.class.isAssignableFrom(repositoryInterface);
        boolean kRepository = KRepository.class.isAssignableFrom(repositoryInterface);
        if (jRepository && kRepository) {
            throw new IllegalStateException(
                    "Illegal repository interface \"" +
                            repositoryInterface.getName() +
                            "\", it can not extend both \"" +
                            JRepository.class.getName() +
                            "\" and \"" +
                            KRepository.class.getName() +
                            "\"");
        }
        if (repositoryInterface.getTypeParameters().length != 0) {
            throw new IllegalStateException(
                    "Illegal repository interface \"" +
                            repositoryInterface.getName() +
                            "\", It itself must not contain any generic parameters, " +
                            "because it must solidify the generic parameters for the super interface \"" +
                            (jRepository ? JRepository.class : KRepository.class).getName() +
                            "\"");
        }
        ClassCodeWriter writer = jRepository ? new JavaClassCodeWriter(metadata) : new KotlinClassCodeWriter(metadata);
        byte[] bytecode = writer.write();
        return JavaClasses.define(bytecode, repositoryInterface);
    }

    public byte[] getTargetRepositoryBytes() {
        Class<?> repositoryInterface = this.metadata.getRepositoryInterface();
        boolean jRepository = JRepository.class.isAssignableFrom(repositoryInterface);
        boolean kRepository = KRepository.class.isAssignableFrom(repositoryInterface);
        if (jRepository && kRepository) {
            throw new IllegalStateException(
                    "Illegal repository interface \"" +
                            repositoryInterface.getName() +
                            "\", it can not extend both \"" +
                            JRepository.class.getName() +
                            "\" and \"" +
                            KRepository.class.getName() +
                            "\"");
        }
        if (repositoryInterface.getTypeParameters().length != 0) {
            throw new IllegalStateException(
                    "Illegal repository interface \"" +
                            repositoryInterface.getName() +
                            "\", It itself must not contain any generic parameters, " +
                            "because it must solidify the generic parameters for the super interface \"" +
                            (jRepository ? JRepository.class : KRepository.class).getName() +
                            "\"");
        }
        ClassCodeWriter writer = jRepository ? new JavaClassCodeWriter(metadata) : new KotlinClassCodeWriter(metadata);
        return writer.write();
    }

    //    public void writeTargetRepository() {
    //        classOutput.write(getTargetRepositoryClass().getName(), getTargetRepositoryBytes());
    //    }
}
