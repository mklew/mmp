/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.mpp.transaction.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.utils.UUIDs;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.EmptyIterators;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.TransactionalMutation;
import org.apache.cassandra.db.partitions.PartitionIterator;
import org.apache.cassandra.db.partitions.PartitionIterators;
import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.rows.UnfilteredRowIterators;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.mpp.transaction.MppService;
import org.apache.cassandra.mpp.transaction.PrivateMemtableStorage;
import org.apache.cassandra.mpp.transaction.TransactionData;
import org.apache.cassandra.mpp.transaction.TransactionId;
import org.apache.cassandra.mpp.transaction.TransactionTimeUUID;
import org.apache.cassandra.mpp.transaction.client.TransactionItem;
import org.apache.cassandra.mpp.transaction.client.TransactionState;
import org.apache.cassandra.mpp.transaction.client.TransactionStateUtils;
import org.apache.cassandra.utils.FBUtilities;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * @author Marek Lewandowski <marek.m.lewandowski@gmail.com>
 * @since 20/01/16
 */
public class MppServiceImpl implements MppService
{
    private static final Logger logger = LoggerFactory.getLogger(MppServiceImpl.class);

    private PrivateMemtableStorage privateMemtableStorage;

    public PrivateMemtableStorage getPrivateMemtableStorage()
    {
        return privateMemtableStorage;
    }

    public void setPrivateMemtableStorage(PrivateMemtableStorage privateMemtableStorage)
    {
        this.privateMemtableStorage = privateMemtableStorage;
    }

    public TransactionState startTransaction()
    {
        final UUID txId = UUIDs.timeBased();
        logger.info("Begin transaction id {}", txId);
        return new TransactionState(txId, Collections.emptyList());
    }

    public void commitTransaction()
    {
        // TODO [MPP] Implement it
        throw new NotImplementedException();
    }

    public void rollbackTransaction(TransactionState transactionState)
    {
        // TODO [MPP] Implement it
        throw new NotImplementedException();
    }

    public void rollbackTransactionLocal(TransactionState transactionState)
    {
        logger.info("Rollback transaction local txState: " + transactionState);
        privateMemtableStorage.removePrivateData(transactionState.id());
    }

    public Map<TransactionItem, List<PartitionUpdate>> readTransactionDataLocalOnly(TransactionId transactionId)
    {
        // TODO [MPP] Implement it
        throw new NotImplementedException();
    }

    public Map<TransactionItem, List<PartitionUpdate>> readAllTransactionData(TransactionState transactionState)
    {
        // TODO [MPP] Implement it
        throw new NotImplementedException();
    }

    public Collection<TransactionId> getInProgressTransactions()
    {
        return null;
    }

    @Override
    public TransactionItem executeTransactionalMutationLocally(TransactionalMutation transactionalMutation)
    {
        final DecoratedKey key = getTransactionalMutationsKey(transactionalMutation);
        final Token token = key.getToken();
        final UUID transactionId = transactionalMutation.getTransactionId();

        logger.info("Execute transaction {} mutation's key is {} token is {} ", transactionId, key, token);
        privateMemtableStorage.storeMutation(new TransactionTimeUUID(transactionId), transactionalMutation.getMutation());

        final String cfName = getColumnFamilyName(transactionalMutation);

        return getTransactionItem(transactionalMutation, token, cfName);
    }

    private static DecoratedKey getTransactionalMutationsKey(TransactionalMutation transactionalMutation)
    {
        return transactionalMutation.getMutation().key();
    }

    private static TransactionItem getTransactionItem(TransactionalMutation transactionalMutation, Token token, String cfName)
    {
        return new TransactionItem(token, transactionalMutation.getKeyspaceName(), cfName);
    }

    public TransactionItem getTransactionItemForMutationNoExecution(TransactionalMutation transactionalMutation)
    {
        return getTransactionItem(transactionalMutation, getTransactionalMutationsKey(transactionalMutation).getToken(),
                                  getColumnFamilyName(transactionalMutation));
    }

    public boolean transactionExistsOnThisNode(TransactionId transactionId)
    {
        return privateMemtableStorage.transactionExistsInStorage(transactionId);
    }

    public TransactionState readLocalTransactionState(TransactionId transactionId)
    {
        logger.info("Execute readLocalTransactionState transactionId {} ", transactionId);
        final TransactionData transactionData = privateMemtableStorage.readTransactionData(transactionId);
        final Collection<TransactionItem> transactionItems = transactionData.asTransactionItems();

        final TransactionState transactionState = TransactionStateUtils.recreateTransactionState(transactionId, transactionItems);

        logger.info("Execute readLocalTransactionState transactionId {} returns transaction state ", transactionId, transactionState);

        return transactionState;
    }

    public void readAllByColumnFamily(TransactionId transactionId, String ksName, String cfName, Consumer<PartitionIterator> consumer)
    {
        logger.info("Execute readAllByColumnFamily transactionId: {} keyspaceName: {} columnFamilyName: {}", transactionId, ksName, cfName);
        final TransactionData transactionData = privateMemtableStorage.readTransactionData(transactionId);
        final UUID cfId = Schema.instance.getId(ksName, cfName);

        int nowInSec = FBUtilities.nowInSeconds();
        final List<PartitionIterator> partitionIterators = transactionData.partitionUpdatesStream(ksName, cfId)
                                                               .map(pu -> pu.unfilteredIterator())
                                                               .map(unfilteredI -> UnfilteredRowIterators.filter(unfilteredI, nowInSec))
                                                               .map(PartitionIterators::singletonIterator)
                                                               .collect(Collectors.toList());

        if(partitionIterators.isEmpty()) {
            consumer.accept(EmptyIterators.partition());
        }
        else {
            try(final PartitionIterator partitionIterator = PartitionIterators.concat(partitionIterators)) {
                consumer.accept(partitionIterator);
            }
        }
    }

    public void readAllByColumnFamilyAndToken(TransactionId transactionId, String ksName, String cfName, Token token, Consumer<PartitionIterator> consumer)
    {
        logger.info("Execute readAllByColumnFamilyAndToken transactionId: {} keyspaceName: {} columnFamilyName: {} token: {}", transactionId, ksName, cfName, token);
        // TODO [MPP] Refactor to make it DRY
        final TransactionData transactionData = privateMemtableStorage.readTransactionData(transactionId);
        final UUID cfId = Schema.instance.getId(ksName, cfName);

        int nowInSec = FBUtilities.nowInSeconds();
        final List<PartitionIterator> partitionIterators = transactionData.readData(ksName, cfId, token)
                                                                          .map(pu -> pu.unfilteredIterator())
                                                                          .map(unfilteredI -> UnfilteredRowIterators.filter(unfilteredI, nowInSec))
                                                                          .map(PartitionIterators::singletonIterator)
                                                                          .collect(Collectors.toList());

        if(partitionIterators.isEmpty()) {
            consumer.accept(EmptyIterators.partition());
        }
        else {
            try(final PartitionIterator partitionIterator = PartitionIterators.concat(partitionIterators)) {
                consumer.accept(partitionIterator);
            }
        }
    }

    private String getColumnFamilyName(TransactionalMutation transactionalMutation)
    {
        final Collection<UUID> columnFamilyIds = transactionalMutation.getColumnFamilyIds();
        Preconditions.checkArgument(columnFamilyIds.size() == 1, "TransactionalMutation should modify only single table");
        final UUID cfId = columnFamilyIds.iterator().next();
        return Keyspace.open(transactionalMutation.getKeyspaceName()).getColumnFamilyStore(cfId).getTableName();
    }
}
