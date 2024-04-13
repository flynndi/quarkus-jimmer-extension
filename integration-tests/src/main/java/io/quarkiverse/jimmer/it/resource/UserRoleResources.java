package io.quarkiverse.jimmer.it.resource;

import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.service.IUserRoleService;

@Path("/userRoleResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserRoleResources {

    private final IUserRoleService iUserRoleService;

    public UserRoleResources(IUserRoleService iUserRoleService) {
        this.iUserRoleService = iUserRoleService;
    }

    @GET
    @Path("/userRole")
    public Response getUserRoleById(@RestQuery UUID id) {
        return Response.ok(iUserRoleService.findById(id)).build();
    }

    @PUT
    @Path("/userRole")
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
}
