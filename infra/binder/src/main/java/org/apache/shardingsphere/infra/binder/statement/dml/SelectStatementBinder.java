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

package org.apache.shardingsphere.infra.binder.statement.dml;

import lombok.SneakyThrows;
import org.apache.shardingsphere.infra.binder.segment.combine.CombineSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.from.TableSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.from.TableSegmentBinderContext;
import org.apache.shardingsphere.infra.binder.segment.lock.LockSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.projection.ProjectionsSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.where.WhereSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.with.WithSegmentBinder;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementBinder;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementBinderContext;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.table.TableSegment;
import org.apache.shardingsphere.sql.parser.statement.core.statement.dml.SelectStatement;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Select statement binder.
 */
public final class SelectStatementBinder implements SQLStatementBinder<SelectStatement> {
    
    @Override
    public SelectStatement bind(final SelectStatement sqlStatement, final ShardingSphereMetaData metaData, final String currentDatabaseName) {
        return bind(sqlStatement, metaData, currentDatabaseName, Collections.emptyMap(), Collections.emptyMap());
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private SelectStatement bind(final SelectStatement sqlStatement, final ShardingSphereMetaData metaData, final String currentDatabaseName,
                                 final Map<String, TableSegmentBinderContext> outerTableBinderContexts, final Map<String, TableSegmentBinderContext> externalTableBinderContexts) {
        SelectStatement result = sqlStatement.getClass().getDeclaredConstructor().newInstance();
        Map<String, TableSegmentBinderContext> tableBinderContexts = new LinkedHashMap<>();
        SQLStatementBinderContext statementBinderContext = new SQLStatementBinderContext(metaData, currentDatabaseName, sqlStatement.getDatabaseType(), sqlStatement.getVariableNames());
        statementBinderContext.getExternalTableBinderContexts().putAll(externalTableBinderContexts);
        sqlStatement.getWithSegment()
                .ifPresent(optional -> result.setWithSegment(WithSegmentBinder.bind(optional, statementBinderContext, tableBinderContexts, statementBinderContext.getExternalTableBinderContexts())));
        Optional<TableSegment> boundedTableSegment = sqlStatement.getFrom().map(optional -> TableSegmentBinder.bind(optional, statementBinderContext, tableBinderContexts, outerTableBinderContexts));
        boundedTableSegment.ifPresent(result::setFrom);
        result.setProjections(ProjectionsSegmentBinder.bind(sqlStatement.getProjections(), statementBinderContext, boundedTableSegment.orElse(null), tableBinderContexts, outerTableBinderContexts));
        sqlStatement.getWhere().ifPresent(optional -> result.setWhere(WhereSegmentBinder.bind(optional, statementBinderContext, tableBinderContexts, outerTableBinderContexts)));
        // TODO support other segment bind in select statement
        sqlStatement.getGroupBy().ifPresent(result::setGroupBy);
        sqlStatement.getHaving().ifPresent(result::setHaving);
        sqlStatement.getOrderBy().ifPresent(result::setOrderBy);
        sqlStatement.getCombine().ifPresent(optional -> result.setCombine(CombineSegmentBinder.bind(optional, statementBinderContext)));
        sqlStatement.getLimit().ifPresent(result::setLimit);
        sqlStatement.getLock().ifPresent(optional -> result.setLock(LockSegmentBinder.bind(optional, statementBinderContext, tableBinderContexts, outerTableBinderContexts)));
        sqlStatement.getWindow().ifPresent(result::setWindow);
        sqlStatement.getModelSegment().ifPresent(result::setModelSegment);
        result.addParameterMarkerSegments(sqlStatement.getParameterMarkerSegments());
        result.getCommentSegments().addAll(sqlStatement.getCommentSegments());
        return result;
    }
    
    /**
     * Bind correlate subquery select statement.
     *
     * @param sqlStatement subquery select statement
     * @param metaData meta data
     * @param currentDatabaseName current database name
     * @param outerTableBinderContexts outer select statement table binder contexts
     * @param externalTableBinderContexts external table binder contexts
     * @return bounded correlate subquery select statement
     */
    public SelectStatement bindCorrelateSubquery(final SelectStatement sqlStatement, final ShardingSphereMetaData metaData, final String currentDatabaseName,
                                                 final Map<String, TableSegmentBinderContext> outerTableBinderContexts, final Map<String, TableSegmentBinderContext> externalTableBinderContexts) {
        return bind(sqlStatement, metaData, currentDatabaseName, outerTableBinderContexts, externalTableBinderContexts);
    }
    
    /**
     * Bind with external table contexts.
     *
     * @param statement select statement
     * @param metaData meta data
     * @param currentDatabaseName current database name
     * @param externalTableContexts external table contexts
     * @return select statement
     */
    public SelectStatement bindWithExternalTableContexts(final SelectStatement statement, final ShardingSphereMetaData metaData, final String currentDatabaseName,
                                                         final Map<String, TableSegmentBinderContext> externalTableContexts) {
        return bind(statement, metaData, currentDatabaseName, Collections.emptyMap(), externalTableContexts);
    }
}
