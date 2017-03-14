package com.facebook.presto.sql.tree;

import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

/**
 * Created by k2data on 16-12-28.
 */
public class CreateFunction extends Statement {

    private final QualifiedName name;

    public CreateFunction(QualifiedName name){
        this(Optional.empty(),name);
    }

    public CreateFunction(NodeLocation location, QualifiedName name){
        this(Optional.of(location),name);
    }

    private CreateFunction(Optional<NodeLocation> location, QualifiedName name){
        super(location);
        this.name = requireNonNull(name,"functionName is null");
    }

    public QualifiedName getName() {
        return name;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitCreateFunction(this, context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        CreateFunction o = (CreateFunction) obj;
        return Objects.equals(name, o.getName());
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("name",name)
                .toString();
    }
}
