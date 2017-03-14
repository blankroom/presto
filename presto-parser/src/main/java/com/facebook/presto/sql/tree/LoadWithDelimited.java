package com.facebook.presto.sql.tree;

import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

/**
 * Created by k2data on 17-2-15.
 */
public class LoadWithDelimited extends Statement {
    private final String hdfsPath;
    private final QualifiedName tableName;

    public LoadWithDelimited(String hdfsPath, QualifiedName tableName) {
        this(Optional.empty(), hdfsPath, tableName);
    }

    public LoadWithDelimited(NodeLocation location, String hdfsPath, QualifiedName tableName) {
        this(Optional.of(location), hdfsPath, tableName);
    }

    private LoadWithDelimited(Optional<NodeLocation> location, String hdfsPath, QualifiedName tableName) {
        super(location);
        this.hdfsPath = requireNonNull(hdfsPath, "hdfsPath is null");
        this.tableName = requireNonNull(tableName, "tableName is null");
    }

    public String getHdfsPath() {
        return hdfsPath;
    }

    public QualifiedName getTableName() {
        return tableName;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
        return visitor.visitLoadWithDelimited(this, context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hdfsPath, tableName);
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj){
            return true;
        }
        if(obj == null || getClass() != obj.getClass()){
            return false;
        }

        LoadWithDelimited o = (LoadWithDelimited) obj;

        return Objects.equals(hdfsPath, o.hdfsPath)
                && Objects.equals(tableName, o.tableName);
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("hdfsPath", hdfsPath)
                .add("tableName", tableName)
                .toString();
    }
}
