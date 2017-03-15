/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.sql.tree;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

public class CreateTableWithFiber extends Statement
{
    private final QualifiedName tableName;
    private final QualifiedName functionName;
    private final List<TableElement> elements;
    private final String partitionName;
    private final String timeStamp;

    public CreateTableWithFiber(QualifiedName tableName, QualifiedName functionName, List<TableElement> elements, String partitionName, String timeStamp)
    {
        this(Optional.empty(), tableName, functionName, elements, partitionName, timeStamp);
    }

    public CreateTableWithFiber(NodeLocation location, QualifiedName tableName, QualifiedName functionName, List<TableElement> elements, String partitionName, String timeStamp)
    {
        this(Optional.of(location), tableName, functionName, elements, partitionName, timeStamp);
    }

    private CreateTableWithFiber(Optional<NodeLocation> location, QualifiedName tableName, QualifiedName functionName, List<TableElement> elements, String partitionName, String timeStamp)
    {
        super(location);
        this.tableName = requireNonNull(tableName, "tableName is null");
        this.functionName = requireNonNull(functionName, "functionName is null");
        this.elements = elements;
        this.partitionName = partitionName;
        this.timeStamp = timeStamp;
    }

    public QualifiedName getTableName()
    {
        return tableName;
    }

    public QualifiedName getFunctionName()
    {
        return functionName;
    }

    public List<TableElement> getElements()
    {
        return elements;
    }

    public String getPartitionName()
    {
        return partitionName;
    }

    public String getTimeStamp()
    {
        return timeStamp;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context)
    {
        return visitor.visitCreateTableWithFiber(this, context);
    }

    public int hashCode()
    {
        return Objects.hash(tableName, functionName, elements, partitionName, timeStamp);
    }

    public boolean equals(Object obj)
    {
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

    public String toString()
    {
        return toStringHelper(this)
                .add("tableName", tableName)
                .add("functionName", functionName)
                .add("elementts", elements)
                .add("partitionName", partitionName)
                .add("timeStamp", timeStamp)
                .toString();
    }
}
