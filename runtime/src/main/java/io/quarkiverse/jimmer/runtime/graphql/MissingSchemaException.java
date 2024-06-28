package io.quarkiverse.jimmer.runtime.graphql;

@SuppressWarnings("serial")
public class MissingSchemaException extends RuntimeException {

    public MissingSchemaException() {
        super("No GraphQL schema definition was configured.");
    }
}
