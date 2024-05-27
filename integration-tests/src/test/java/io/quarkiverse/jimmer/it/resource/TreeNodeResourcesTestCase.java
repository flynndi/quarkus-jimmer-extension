package io.quarkiverse.jimmer.it.resource;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.IntegrationTestsProfile;
import io.quarkiverse.jimmer.it.repository.TreeNodeRepository;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(IntegrationTestsProfile.class)
public class TreeNodeResourcesTestCase {

    @Inject
    TreeNodeRepository treeNodeRepository;

    @Test
    public void testTreeNodeRepository() {
        TreeNodeRepository treeNodeRepository = Arc.container().instance(TreeNodeRepository.class).get();
        Assertions.assertEquals(treeNodeRepository, this.treeNodeRepository);
    }
}
