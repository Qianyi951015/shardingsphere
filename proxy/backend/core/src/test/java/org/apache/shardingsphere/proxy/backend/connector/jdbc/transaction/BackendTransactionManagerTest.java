/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.proxy.backend.connector.jdbc.transaction;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import lombok.SneakyThrows;
import org.apache.shardingsphere.infra.metadata.database.rule.RuleMetaData;
import org.apache.shardingsphere.infra.session.connection.ConnectionContext;
import org.apache.shardingsphere.infra.session.connection.transaction.TransactionConnectionContext;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.proxy.backend.connector.ProxyDatabaseConnectionManager;
import org.apache.shardingsphere.proxy.backend.context.ProxyContext;
import org.apache.shardingsphere.proxy.backend.session.ConnectionSession;
import org.apache.shardingsphere.proxy.backend.session.transaction.TransactionStatus;
import org.apache.shardingsphere.test.mock.AutoMockExtension;
import org.apache.shardingsphere.test.mock.StaticMockSettings;
import org.apache.shardingsphere.transaction.ShardingSphereTransactionManagerEngine;
import org.apache.shardingsphere.transaction.api.TransactionType;
import org.apache.shardingsphere.transaction.rule.TransactionRule;
import org.apache.shardingsphere.transaction.spi.ShardingSphereDistributionTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.internal.configuration.plugins.Plugins;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(AutoMockExtension.class)
@StaticMockSettings(ProxyContext.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackendTransactionManagerTest {
    
    @Mock
    private ConnectionSession connectionSession;
    
    @Mock
    private ProxyDatabaseConnectionManager databaseConnectionManager;
    
    @Mock
    private TransactionStatus transactionStatus;
    
    @Mock
    private LocalTransactionManager localTransactionManager;
    
    @Mock
    private ShardingSphereDistributionTransactionManager distributionTransactionManager;
    
    @Mock
    private Connection connection;
    
    private BackendTransactionManager backendTransactionManager;
    
    @BeforeEach
    void setUp() {
        when(connectionSession.getTransactionStatus()).thenReturn(transactionStatus);
        when(databaseConnectionManager.getConnectionSession()).thenReturn(connectionSession);
        when(databaseConnectionManager.getCachedConnections()).thenReturn(mockCachedConnections());
        ConnectionContext connectionContext = mock(ConnectionContext.class);
        when(connectionSession.getConnectionContext()).thenReturn(connectionContext);
        TransactionConnectionContext context = new TransactionConnectionContext();
        when(connectionContext.getTransactionContext()).thenReturn(context);
    }
    
    private Multimap<String, Connection> mockCachedConnections() {
        Multimap<String, Connection> result = HashMultimap.create();
        result.putAll("ds1", Collections.singleton(connection));
        return result;
    }
    
    @Test
    void assertBeginForLocalTransaction() {
        ContextManager contextManager = mockContextManager(TransactionType.LOCAL);
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        newBackendTransactionManager(TransactionType.LOCAL, false);
        backendTransactionManager.begin();
        verify(transactionStatus).setInTransaction(true);
        verify(databaseConnectionManager).closeHandlers(true);
        verify(databaseConnectionManager).closeConnections(false);
        verify(localTransactionManager).begin();
    }
    
    @Test
    void assertBeginForDistributedTransaction() {
        ContextManager contextManager = mockContextManager(TransactionType.XA);
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        newBackendTransactionManager(TransactionType.XA, true);
        backendTransactionManager.begin();
        verify(transactionStatus, times(0)).setInTransaction(true);
        verify(databaseConnectionManager, times(0)).closeConnections(false);
        verify(distributionTransactionManager).begin();
    }
    
    @Test
    void assertCommitForLocalTransaction() throws SQLException {
        ContextManager contextManager = mockContextManager(TransactionType.LOCAL);
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        newBackendTransactionManager(TransactionType.LOCAL, true);
        backendTransactionManager.commit();
        verify(transactionStatus).setInTransaction(false);
        verify(localTransactionManager).commit();
    }
    
    @Test
    void assertCommitForDistributedTransaction() throws SQLException {
        ContextManager contextManager = mockContextManager(TransactionType.XA);
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        newBackendTransactionManager(TransactionType.XA, true);
        backendTransactionManager.commit();
        verify(transactionStatus).setInTransaction(false);
        verify(distributionTransactionManager).commit(false);
    }
    
    @Test
    void assertCommitWithoutTransaction() throws SQLException {
        ContextManager contextManager = mockContextManager(TransactionType.LOCAL);
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        newBackendTransactionManager(TransactionType.LOCAL, false);
        backendTransactionManager.commit();
        verify(transactionStatus, times(0)).setInTransaction(false);
        verify(localTransactionManager, times(0)).commit();
        verify(distributionTransactionManager, times(0)).commit(false);
    }
    
    @Test
    void assertRollbackForLocalTransaction() throws SQLException {
        ContextManager contextManager = mockContextManager(TransactionType.LOCAL);
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        newBackendTransactionManager(TransactionType.LOCAL, true);
        backendTransactionManager.rollback();
        verify(transactionStatus).setInTransaction(false);
        verify(localTransactionManager).rollback();
    }
    
    @Test
    void assertRollbackForDistributedTransaction() throws SQLException {
        ContextManager contextManager = mockContextManager(TransactionType.XA);
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        newBackendTransactionManager(TransactionType.XA, true);
        backendTransactionManager.rollback();
        verify(transactionStatus).setInTransaction(false);
        verify(distributionTransactionManager).rollback();
    }
    
    @Test
    void assertRollbackWithoutTransaction() throws SQLException {
        ContextManager contextManager = mockContextManager(TransactionType.LOCAL);
        when(ProxyContext.getInstance().getContextManager()).thenReturn(contextManager);
        newBackendTransactionManager(TransactionType.LOCAL, false);
        backendTransactionManager.rollback();
        verify(transactionStatus, times(0)).setInTransaction(false);
        verify(localTransactionManager, times(0)).rollback();
        verify(distributionTransactionManager, times(0)).rollback();
    }
    
    private void newBackendTransactionManager(final TransactionType transactionType, final boolean inTransaction) {
        when(ProxyContext.getInstance().getContextManager().getMetaDataContexts().getMetaData().getGlobalRuleMetaData().getSingleRule(TransactionRule.class).getDefaultType())
                .thenReturn(transactionType);
        when(transactionStatus.isInTransaction()).thenReturn(inTransaction);
        backendTransactionManager = new BackendTransactionManager(databaseConnectionManager);
        setLocalTransactionManager();
        setTransactionHooks();
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private void setLocalTransactionManager() {
        Plugins.getMemberAccessor().set(BackendTransactionManager.class.getDeclaredField("localTransactionManager"), backendTransactionManager, localTransactionManager);
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private void setTransactionHooks() {
        Plugins.getMemberAccessor().set(BackendTransactionManager.class.getDeclaredField("transactionHooks"), backendTransactionManager, Collections.emptyMap());
    }
    
    private ContextManager mockContextManager(final TransactionType transactionType) {
        ContextManager result = mock(ContextManager.class, RETURNS_DEEP_STUBS);
        RuleMetaData globalRuleMetaData = mockGlobalRuleMetaData(transactionType);
        when(result.getMetaDataContexts().getMetaData().getGlobalRuleMetaData()).thenReturn(globalRuleMetaData);
        return result;
    }
    
    private RuleMetaData mockGlobalRuleMetaData(final TransactionType transactionType) {
        ShardingSphereTransactionManagerEngine transactionManagerEngine = mock(ShardingSphereTransactionManagerEngine.class);
        when(transactionManagerEngine.getTransactionManager(TransactionType.XA)).thenReturn(distributionTransactionManager);
        TransactionRule transactionRule = mock(TransactionRule.class);
        when(transactionRule.getDefaultType()).thenReturn(transactionType);
        when(transactionRule.getResource()).thenReturn(transactionManagerEngine);
        return new RuleMetaData(Collections.singleton(transactionRule));
    }
}
