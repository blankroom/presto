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

import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.requireNonNull;

/**
 * Created by k2data on 17-2-15.
 */
public class LoadWithDelimited extends Statement
{
    private final String hdfsPath;
    private final QualifiedName tableName;

    public LoadWithDelimited(String hdfsPath, QualifiedName tableName)
    {
        this(Optional.empty(), hdfsPath, tableName);
    }

    public LoadWithDelimited(NodeLocation location, String hdfsPath, QualifiedName tableName)
    {
        this(Optional.of(location), hdfsPath, tableName);
    }

    private LoadWithDelimited(Optional<NodeLocation> location, String hdfsPath, QualifiedName tableName)
    {
        super(location);
        this.hdfsPath = requireNonNull(hdfsPath, "hdfsPath is null");
        this.tableName = requireNonNull(tableName, "tableName is null");
    }

    public String getHdfsPath()
    {
        return hdfsPath;
    }

    public QualifiedName getTableName()
    {
        return tableName;
    }

    @Override
    public <R, C> R accept(AstVisitor<R, C> visitor, C context)
    {
        return visitor.visitLoadWithDelimited(this, context);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(hdfsPath, tableName);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        LoadWithDelimited o = (LoadWithDelimited) obj;

        return Objects.equals(hdfsPath, o.hdfsPath)
                && Objects.equals(tableName, o.tableName);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("hdfsPath", hdfsPath)
                .add("tableName", tableName)
                .toString();
    }
}
