package io.quarkiverse.jimmer.it.resource;

import java.util.List;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.client.meta.Api;
import org.jboss.resteasy.reactive.RestQuery;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.entity.dto.TreeNodeDetailView;
import io.quarkiverse.jimmer.it.repository.TreeNodeRepository;

@Path("/treeNodeResources")
@Produces(MediaType.APPLICATION_JSON)
@Api("TreeNode")
public class TreeNodeResources {

    private final TreeNodeRepository treeNodeRepository;

    public TreeNodeResources(TreeNodeRepository treeNodeRepository) {
        this.treeNodeRepository = treeNodeRepository;
    }

    @GET
    @Path("/all")
    @Api
    public List<TreeNodeDetailView> all(@RestQuery @Nullable Long parentId) {
        return treeNodeRepository.findDetails(parentId);
    }
}
