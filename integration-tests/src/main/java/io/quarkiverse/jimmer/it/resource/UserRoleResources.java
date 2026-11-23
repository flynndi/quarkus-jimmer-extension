package io.quarkiverse.jimmer.it.resource;

import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.babyfish.jimmer.client.meta.Api;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkiverse.jimmer.it.entity.dto.UserRoleInput;
import io.quarkiverse.jimmer.it.entity.dto.UserRoleSpecification;
import io.quarkiverse.jimmer.it.repository.UserRoleJavaRepository;

@Path("/userRoleResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api("test")
public class UserRoleResources {

    private final UserRoleJavaRepository userRoleRepository;

    public UserRoleResources(UserRoleJavaRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    @POST
    @Transactional(rollbackOn = Exception.class)
    @Api
    public UserRole create(UserRoleInput input) {
        return userRoleRepository.save(input, SaveMode.INSERT_ONLY).getModifiedEntity();
    }

    @PUT
    @ResponseStatus(200)
    @Transactional(rollbackOn = Exception.class)
    @Api
    public void update(UserRoleInput input) {
        userRoleRepository.save(input, SaveMode.UPDATE_ONLY);
    }

    @GET
    @Path("/userRoleFindById")
    public Response userRoleFindById(@RestQuery UUID id) {
        return Response.ok(userRoleRepository.findById(id)).build();
    }

    @DELETE
    @Path("/delete")
    @Transactional(rollbackOn = Exception.class)
    public Response delete(@RestQuery UUID id) {
        userRoleRepository.deleteById(id);
        return Response.ok().build();
    }

    @GET
    @Path("/deleteReverseById")
    public Response deleteReverseById(@RestQuery UUID id) {
        return Response.ok(userRoleRepository.findDeletedById(id)).build();
    }

    @GET
    @Path("/testUserRoleSpecification")
    public Response testUserRoleSpecification(UserRoleSpecification userRoleSpecification) {
        return Response.ok(userRoleRepository.find(userRoleSpecification)).build();
    }
}
