package io.quarkiverse.jimmer.graphql.apt;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class JimmerGraphQLProcessor extends AbstractProcessor {

    private final Map<String, JimmerGraphQLSourceType> collectedTypes = new LinkedHashMap<>();

    private final Set<String> emittedFqns = new LinkedHashSet<>();

    private JimmerGraphQLElementScanner scanner;

    private boolean generated;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.scanner = new JimmerGraphQLElementScanner(processingEnv.getElementUtils());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (generated || roundEnv.processingOver()) {
            return false;
        }
        collectRootTypes(roundEnv);
        if (collectedTypes.isEmpty()) {
            return false;
        }
        generateSources();
        generated = true;
        return false;
    }

    private void collectRootTypes(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getRootElements()) {
            if (element instanceof TypeElement type && type.getKind().isInterface()) {
                collectType(type);
            }
        }
    }

    private void collectType(TypeElement type) {
        String qualifiedName = type.getQualifiedName().toString();
        if (collectedTypes.containsKey(qualifiedName)) {
            return;
        }
        JimmerGraphQLSourceKind kind = scanner.sourceKind(type);
        if (kind != JimmerGraphQLSourceKind.ENTITY && kind != JimmerGraphQLSourceKind.MAPPED_SUPERCLASS) {
            return;
        }
        collectedTypes.put(qualifiedName, scanner.scanType(type));
        for (var parentMirror : type.getInterfaces()) {
            if (parentMirror instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement parent) {
                collectType(parent);
            }
        }
    }

    private void generateSources() {
        JimmerGraphQLSourceModel model = new JimmerGraphQLSourceModel(new ArrayList<>(collectedTypes.values()));
        JimmerGraphQLSourceGenerator generator = new JimmerGraphQLSourceGenerator(model);
        for (Map.Entry<String, String> entry : generator.generate().entrySet()) {
            String qualifiedName = entry.getKey();
            if (!emittedFqns.add(qualifiedName)) {
                throw new IllegalStateException(
                        "GraphQL facade source generated twice in the same compilation: " + qualifiedName);
            }
            try {
                JavaFileObject file = processingEnv.getFiler().createSourceFile(qualifiedName);
                try (Writer writer = file.openWriter()) {
                    writer.write(entry.getValue());
                }
            } catch (IOException ex) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Cannot write generated GraphQL source " + qualifiedName + ": " + ex.getMessage());
                throw new IllegalStateException("Cannot write generated GraphQL source " + qualifiedName, ex);
            }
        }
    }
}
