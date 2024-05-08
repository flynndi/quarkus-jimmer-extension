package io.quarkiverse.jimmer.it.resource;

import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.entity.dto.UserRoleSpecification;
import io.quarkiverse.jimmer.it.repository.UserRoleRepository;
import io.quarkiverse.jimmer.it.service.IUserRoleService;
import io.quarkus.agroal.DataSource;

@Path("/userRoleResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserRoleResources {

    private final IUserRoleService iUserRoleService;

    private final UserRoleRepository userRoleRepository;

    public UserRoleResources(IUserRoleService iUserRoleService, @DataSource("DB2") UserRoleRepository userRoleRepository) {
        this.iUserRoleService = iUserRoleService;
        this.userRoleRepository = userRoleRepository;
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
