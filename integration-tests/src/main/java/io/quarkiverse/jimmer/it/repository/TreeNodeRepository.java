package io.quarkiverse.jimmer.it.repository;

import java.util.List;

import io.quarkiverse.jimmer.it.entity.Tables;
import io.quarkiverse.jimmer.it.entity.TreeNode;
import io.quarkiverse.jimmer.it.entity.dto.TreeNodeDetailView;
import io.quarkiverse.jimmer.runtime.repository.JRepository;

public interface TreeNodeRepository extends JRepository<TreeNode, Long> {

    default List<TreeNodeDetailView> infiniteRecursion(Long parentId) {
        return sql()
                .createQuery(Tables.TREE_NODE_TABLE)
                .where(Tables.TREE_NODE_TABLE.parentId().eq(parentId))
                .select(Tables.TREE_NODE_TABLE.fetch(TreeNodeDetailView.class))
                .execute();
    }
}
