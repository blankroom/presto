package com.facebook.presto.execution;

import com.facebook.presto.metadata.Metadata;
import com.facebook.presto.security.AccessControl;
import com.facebook.presto.sql.tree.Expression;
import com.facebook.presto.sql.tree.LoadWithDelimited;
import com.facebook.presto.transaction.TransactionManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Created by blankroom on 2017/4/4.
 */
public class LoadWithDelimitedTask implements DataDefinitionTask<LoadWithDelimited>
{

    @Override
    public String getName() {
        return null;
    }

    @Override
    public CompletableFuture<?> execute(LoadWithDelimited statement, TransactionManager transactionManager, Metadata metadata, AccessControl accessControl, QueryStateMachine stateMachine, List<Expression> parameters) {
        return null;
    }
}
