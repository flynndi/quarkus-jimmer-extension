package io.quarkiverse.jimmer.it.repository;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.jimmer.it.entity.TreeNode;
import io.quarkiverse.jimmer.runtime.repository.JRepository;

@ApplicationScoped
public class TreeNodeRepository implements JRepository<TreeNode, Long> {
}
