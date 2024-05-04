package io.quarkiverse.jimmer.it.entity;

import java.util.List;

import org.babyfish.jimmer.sql.*;
import org.jetbrains.annotations.Nullable;

@Entity
public interface TreeNode extends BaseEntity {

    @Id
    @Column(name = "NODE_ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    @Key
    String name();

    @Key
    @Nullable
    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    TreeNode parent();

    @OneToMany(mappedBy = "parent", orderedProps = @OrderedProp("name"))
    List<TreeNode> childNodes();
}
