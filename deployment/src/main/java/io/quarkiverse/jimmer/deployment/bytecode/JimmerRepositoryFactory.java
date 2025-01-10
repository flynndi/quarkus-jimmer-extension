package io.quarkiverse.jimmer.deployment.bytecode;

import org.jetbrains.annotations.NotNull;

import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.KRepository;

public class JimmerRepositoryFactory {

    private final Class<?> repositoryInterface;

    private final byte[] bytecode;

    public JimmerRepositoryFactory(RepositoryMetadata metadata) {
        this.repositoryInterface = metadata.getRepositoryInterface();
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
        ClassCodeWriter classCodeWriter = jRepository ? new JavaClassCodeWriter(metadata) : new KotlinClassCodeWriter(metadata);
        this.bytecode = classCodeWriter.write();
    }

    @NotNull
    public Class<?> getTargetRepositoryClass() {
        return JavaClasses.define(this.bytecode, this.repositoryInterface);
    }

    public byte[] getTargetRepositoryBytes() {
        return this.bytecode;
    }
}
