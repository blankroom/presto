package com.facebook.presto.sql.tree;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

/**
 * Created by 虚室空白 on 2016/12/18.
 */
public class CreateTableWithFiber extends Statement {
    private final QualifiedName tableName;
    private final QualifiedName functionName;
    private final List<TableElement> elements;
    private final String partitionName;
    private final String timeStamp;


    public CreateTableWithFiber(QualifiedName tableName, QualifiedName functionName, List<TableElement> elements, String partitionName, String timeStamp) {
        this(Optional.empty(),tableName,functionName,elements,partitionName,timeStamp);
    }

    public CreateTableWithFiber(NodeLocation location, QualifiedName tableName, QualifiedName functionName, List<TableElement> elements, String partitionName, String timeStamp) {
        this(Optional.of(location),tableName,functionName,elements,partitionName,timeStamp);
    }

    private CreateTableWithFiber(Optional<NodeLocation> location, QualifiedName tableName, QualifiedName functionName, List<TableElement> elements, String partitionName, String timeStamp) {
        super(location);
        this.tableName = requireNonNull(tableName,"tableName is null");
        this.functionName = requireNonNull(functionName,"functionName is null");
        this.elements = elements;
        this.partitionName = partitionName;
        this.timeStamp = timeStamp;
    }



    public QualifiedName getTableName() {
        return tableName;
    }

    public QualifiedName getFunctionName() {
        return functionName;
    }

    public List<TableElement> getElements() {
        return elements;
    }

    public String getPartitionName() {
        return partitionName;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context)
    {
        return visitor.visitCreateTableWithFiber(this, context);
    }

    public int hashCode() {
        return Objects.hash(tableName,functionName,elements,partitionName,timeStamp);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }
        CreateTableWithFiber o = (CreateTableWithFiber) obj;
        return Objects.equals(tableName, o.tableName)
                && Objects.equals(functionName, o.functionName)
                && Objects.equals(elements, o.elements)
                && Objects.equals(partitionName, o.partitionName)
                && Objects.equals(timeStamp, o.timeStamp);
    }

    public String toString() {
        return toStringHelper(this)
                .add("tableName",tableName)
                .add("functionName",functionName)
                .add("elementts",elements)
                .add("partitionName",partitionName)
                .add("timeStamp",timeStamp)
                .toString();
    }
}
