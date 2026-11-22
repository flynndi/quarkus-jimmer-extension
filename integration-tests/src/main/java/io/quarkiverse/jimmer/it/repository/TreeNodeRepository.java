package io.quarkiverse.jimmer.it.repository;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.entity.TreeNode;
import io.quarkiverse.jimmer.it.entity.TreeNodeTable;
import io.quarkiverse.jimmer.it.entity.dto.TreeNodeDetailView;
import io.quarkiverse.jimmer.runtime.repository.JRepository;

public interface TreeNodeRepository extends JRepository<TreeNode, Long> {

    default List<TreeNodeDetailView> findDetails(@Nullable Long parentId) {
        TreeNodeTable node = TreeNodeTable.$;
        return sql().createQuery(node)
                .where(node.parentId().eqIf(parentId))
                .select(node.fetch(TreeNodeDetailView.class))
                .execute();
    }
}
