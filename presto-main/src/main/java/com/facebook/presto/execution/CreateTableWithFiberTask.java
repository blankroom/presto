package com.facebook.presto.execution;

import com.facebook.presto.metadata.Metadata;
import com.facebook.presto.security.AccessControl;
import com.facebook.presto.sql.tree.CreateTableWithFiber;
import com.facebook.presto.sql.tree.Expression;
import com.facebook.presto.transaction.TransactionManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by blankroom on 2017/3/26.
 */
public class CreateTableWithFiberTask
        implements DataDefinitionTask<CreateTableWithFiber>
{
    @Override
    public String getName() {
        return null;
    }

    @Override
    public CompletableFuture<?> execute(CreateTableWithFiber statement, TransactionManager transactionManager, Metadata metadata, AccessControl accessControl, QueryStateMachine stateMachine, List<Expression> parameters) {
        System.out.println("this is CreateTableWithFiber Task!");
        return null;
    }
}
