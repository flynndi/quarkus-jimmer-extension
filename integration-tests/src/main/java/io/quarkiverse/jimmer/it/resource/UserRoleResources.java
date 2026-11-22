package io.quarkiverse.jimmer.it.resource;

import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.babyfish.jimmer.client.meta.Api;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.entity.dto.UserRoleInput;
import io.quarkiverse.jimmer.it.entity.dto.UserRoleSpecification;
import io.quarkiverse.jimmer.it.repository.UserRoleJavaRepository;
import io.quarkiverse.jimmer.it.service.IUserRoleService;

@Path("/userRoleResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api("test")
public class UserRoleResources {

    private final IUserRoleService iUserRoleService;

    private final UserRoleJavaRepository userRoleRepository;

    public UserRoleResources(IUserRoleService iUserRoleService, UserRoleJavaRepository userRoleRepository) {
        this.iUserRoleService = iUserRoleService;
        this.userRoleRepository = userRoleRepository;
    }

    @POST
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response create(UserRoleInput input) {
        return Response.ok(userRoleRepository.save(input, SaveMode.INSERT_ONLY).getModifiedEntity()).build();
    }

    @PUT
    @Transactional(rollbackOn = Exception.class)
    @Api
    public Response update(UserRoleInput input) {
        userRoleRepository.save(input, SaveMode.UPDATE_ONLY);
        return Response.ok().build();
    }

    @GET
    @Path("/userRoleFindById")
    public Response userRoleFindById(@RestQuery UUID id) {
        return Response.ok(iUserRoleService.findById(id)).build();
    }

    @PUT
    @Path("/updateUserRoleById")
    @Transactional(rollbackOn = Exception.class)
    public Response updateUserRoleById(@RestQuery UUID id) {
        iUserRoleService.updateById(id);
        return Response.ok().build();
    }

    @DELETE
    @Path("/delete")
    @Transactional(rollbackOn = Exception.class)
    public Response delete(@RestQuery UUID id) {
        iUserRoleService.deleteById(id);
        return Response.ok().build();
    }

    @GET
    @Path("/deleteReverseById")
    public Response deleteReverseById(@RestQuery UUID id) {
        return Response.ok(iUserRoleService.deleteReverseById(id)).build();
    }

    @GET
    @Path("/testUserRoleSpecification")
    public Response testUserRoleSpecification(UserRoleSpecification userRoleSpecification) {
        return Response.ok(userRoleRepository.find(userRoleSpecification)).build();
    }
}
