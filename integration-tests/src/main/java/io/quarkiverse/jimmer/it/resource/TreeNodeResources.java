package io.quarkiverse.jimmer.it.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.babyfish.jimmer.client.meta.Api;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkiverse.jimmer.it.repository.TreeNodeRepository;

@Path("/treeNodeResources")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Api("TreeNode")
public class TreeNodeResources {

    private final TreeNodeRepository treeNodeRepository;

    public TreeNodeResources(TreeNodeRepository treeNodeRepository) {
        this.treeNodeRepository = treeNodeRepository;
    }

    @GET
    @Path("/infiniteRecursion")
    @Api
    public Response infiniteRecursion(@RestQuery Long parentId) {
        return Response.ok(treeNodeRepository.infiniteRecursion(parentId)).build();
    }
}
