package io.quarkiverse.jimmer.it;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
@Path("IntegrationTestsApplication")
public class IntegrationTestsApplication implements QuarkusApplication {

    @Override
    public int run(String... args) {
        Quarkus.waitForExit();
        return 0;
    }

    public static void main(String[] args) {
        Quarkus.run(IntegrationTestsApplication.class, args);
    }

    @POST
    @Path("shutdown")
    public void shutdownTaigaQuarkus() {
        Quarkus.asyncExit();
    }
}
