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

import com.cedarsoftware.util.CaseInsensitiveMap;
import lombok.SneakyThrows;
import org.apache.shardingsphere.infra.binder.segment.SegmentType;
import org.apache.shardingsphere.infra.binder.segment.column.InsertColumnsSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.expression.ExpressionSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.expression.impl.ColumnSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.from.TableSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.from.TableSegmentBinderContext;
import org.apache.shardingsphere.infra.binder.segment.parameter.ParameterMarkerSegmentBinder;
import org.apache.shardingsphere.infra.binder.segment.where.WhereSegmentBinder;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementBinder;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementBinderContext;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.assignment.ColumnAssignmentSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.assignment.InsertValuesSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.assignment.SetAssignmentSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.column.ColumnSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.column.InsertColumnsSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.expr.ExpressionSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.expr.ExpressionWithParamsSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.expr.simple.ParameterMarkerExpressionSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.item.ColumnProjectionSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.item.ProjectionSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.ParameterMarkerSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.bounded.ColumnSegmentBoundedInfo;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.table.SimpleTableSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.table.SubqueryTableSegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.generic.table.TableSegment;
import org.apache.shardingsphere.sql.parser.statement.core.statement.dml.InsertStatement;
import org.apache.shardingsphere.sql.parser.statement.core.statement.dml.MergeStatement;
import org.apache.shardingsphere.sql.parser.statement.core.statement.dml.UpdateStatement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Merge statement binder.
 */
public final class MergeStatementBinder implements SQLStatementBinder<MergeStatement> {
    
    @Override
    public MergeStatement bind(final MergeStatement sqlStatement, final ShardingSphereMetaData metaData, final String currentDatabaseName) {
        return bind(sqlStatement, metaData, currentDatabaseName, Collections.emptyMap());
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private MergeStatement bind(final MergeStatement sqlStatement, final ShardingSphereMetaData metaData, final String currentDatabaseName,
                                final Map<String, TableSegmentBinderContext> externalTableBinderContexts) {
        MergeStatement result = sqlStatement.getClass().getDeclaredConstructor().newInstance();
        SQLStatementBinderContext statementBinderContext = new SQLStatementBinderContext(metaData, currentDatabaseName, sqlStatement.getDatabaseType(), sqlStatement.getVariableNames());
        statementBinderContext.getExternalTableBinderContexts().putAll(externalTableBinderContexts);
        Map<String, TableSegmentBinderContext> targetTableBinderContexts = new CaseInsensitiveMap<>();
        TableSegment boundedTargetTableSegment = TableSegmentBinder.bind(sqlStatement.getTarget(), statementBinderContext, targetTableBinderContexts, Collections.emptyMap());
        Map<String, TableSegmentBinderContext> sourceTableBinderContexts = new CaseInsensitiveMap<>();
        TableSegment boundedSourceTableSegment = TableSegmentBinder.bind(sqlStatement.getSource(), statementBinderContext, sourceTableBinderContexts, Collections.emptyMap());
        result.setTarget(boundedTargetTableSegment);
        result.setSource(boundedSourceTableSegment);
        Map<String, TableSegmentBinderContext> tableBinderContexts = new LinkedHashMap<>();
        tableBinderContexts.putAll(sourceTableBinderContexts);
        tableBinderContexts.putAll(targetTableBinderContexts);
        if (null != sqlStatement.getExpression()) {
            ExpressionWithParamsSegment expression = new ExpressionWithParamsSegment(sqlStatement.getExpression().getStartIndex(), sqlStatement.getExpression().getStopIndex(),
                    ExpressionSegmentBinder.bind(sqlStatement.getExpression().getExpr(), SegmentType.JOIN_ON, statementBinderContext, tableBinderContexts, Collections.emptyMap()));
            expression.getParameterMarkerSegments().addAll(sqlStatement.getExpression().getParameterMarkerSegments());
            result.setExpression(expression);
        }
        sqlStatement.getInsert().ifPresent(
                optional -> result.setInsert(bindMergeInsert(optional, (SimpleTableSegment) boundedTargetTableSegment, statementBinderContext, targetTableBinderContexts, sourceTableBinderContexts)));
        sqlStatement.getUpdate().ifPresent(
                optional -> result.setUpdate(bindMergeUpdate(optional, (SimpleTableSegment) boundedTargetTableSegment, statementBinderContext, targetTableBinderContexts, sourceTableBinderContexts)));
        addParameterMarkerSegments(result);
        result.getCommentSegments().addAll(sqlStatement.getCommentSegments());
        return result;
    }
    
    private void addParameterMarkerSegments(final MergeStatement mergeStatement) {
        // TODO bind parameter marker segments for merge statement
        mergeStatement.addParameterMarkerSegments(getSourceSubqueryTableProjectionParameterMarkers(mergeStatement.getSource()));
        mergeStatement.getInsert().ifPresent(optional -> mergeStatement.addParameterMarkerSegments(optional.getParameterMarkerSegments()));
        mergeStatement.getUpdate().ifPresent(optional -> mergeStatement.addParameterMarkerSegments(optional.getParameterMarkerSegments()));
    }
    
    private Collection<ParameterMarkerSegment> getSourceSubqueryTableProjectionParameterMarkers(final TableSegment tableSegment) {
        if (!(tableSegment instanceof SubqueryTableSegment)) {
            return Collections.emptyList();
        }
        SubqueryTableSegment subqueryTable = (SubqueryTableSegment) tableSegment;
        Collection<ParameterMarkerSegment> result = new LinkedList<>();
        for (ProjectionSegment each : subqueryTable.getSubquery().getSelect().getProjections().getProjections()) {
            if (each instanceof ParameterMarkerExpressionSegment) {
                result.add((ParameterMarkerSegment) each);
            }
        }
        return result;
    }
    
    @SneakyThrows
    private InsertStatement bindMergeInsert(final InsertStatement sqlStatement, final SimpleTableSegment tableSegment, final SQLStatementBinderContext statementBinderContext,
                                            final Map<String, TableSegmentBinderContext> targetTableBinderContexts, final Map<String, TableSegmentBinderContext> sourceTableBinderContexts) {
        SQLStatementBinderContext insertStatementBinderContext = new SQLStatementBinderContext(statementBinderContext.getMetaData(), statementBinderContext.getCurrentDatabaseName(),
                statementBinderContext.getDatabaseType(), statementBinderContext.getVariableNames());
        insertStatementBinderContext.getExternalTableBinderContexts().putAll(statementBinderContext.getExternalTableBinderContexts());
        insertStatementBinderContext.getExternalTableBinderContexts().putAll(sourceTableBinderContexts);
        InsertStatement result = sqlStatement.getClass().getDeclaredConstructor().newInstance();
        result.setTable(tableSegment);
        sqlStatement.getInsertColumns()
                .ifPresent(optional -> result.setInsertColumns(InsertColumnsSegmentBinder.bind(sqlStatement.getInsertColumns().get(), statementBinderContext, targetTableBinderContexts)));
        sqlStatement.getInsertSelect().ifPresent(result::setInsertSelect);
        Collection<InsertValuesSegment> insertValues = new LinkedList<>();
        Map<ParameterMarkerSegment, ColumnSegmentBoundedInfo> parameterMarkerSegmentBoundedInfos = new LinkedHashMap<>();
        List<ColumnSegment> columnSegments = new ArrayList<>(result.getInsertColumns().map(InsertColumnsSegment::getColumns)
                .orElseGet(() -> getVisibleColumns(targetTableBinderContexts.values().iterator().next().getProjectionSegments())));
        for (InsertValuesSegment each : sqlStatement.getValues()) {
            List<ExpressionSegment> values = new LinkedList<>();
            int index = 0;
            for (ExpressionSegment expression : each.getValues()) {
                values.add(ExpressionSegmentBinder.bind(expression, SegmentType.VALUES, insertStatementBinderContext, targetTableBinderContexts, sourceTableBinderContexts));
                if (expression instanceof ParameterMarkerSegment) {
                    parameterMarkerSegmentBoundedInfos.put((ParameterMarkerSegment) expression, columnSegments.get(index).getColumnBoundedInfo());
                }
                index++;
            }
            insertValues.add(new InsertValuesSegment(each.getStartIndex(), each.getStopIndex(), values));
        }
        result.getValues().addAll(insertValues);
        sqlStatement.getOnDuplicateKeyColumns().ifPresent(result::setOnDuplicateKeyColumns);
        sqlStatement.getSetAssignment().ifPresent(result::setSetAssignment);
        sqlStatement.getWithSegment().ifPresent(result::setWithSegment);
        sqlStatement.getOutputSegment().ifPresent(result::setOutputSegment);
        sqlStatement.getMultiTableInsertType().ifPresent(result::setMultiTableInsertType);
        sqlStatement.getMultiTableInsertIntoSegment().ifPresent(result::setMultiTableInsertIntoSegment);
        sqlStatement.getMultiTableConditionalIntoSegment().ifPresent(result::setMultiTableConditionalIntoSegment);
        sqlStatement.getReturningSegment().ifPresent(result::setReturningSegment);
        sqlStatement.getWhere().ifPresent(optional -> result.setWhere(WhereSegmentBinder.bind(optional, insertStatementBinderContext, targetTableBinderContexts, sourceTableBinderContexts)));
        result.addParameterMarkerSegments(ParameterMarkerSegmentBinder.bind(sqlStatement.getParameterMarkerSegments(), parameterMarkerSegmentBoundedInfos));
        result.getCommentSegments().addAll(sqlStatement.getCommentSegments());
        return result;
    }
    
    private Collection<ColumnSegment> getVisibleColumns(final Collection<ProjectionSegment> projectionSegments) {
        Collection<ColumnSegment> result = new LinkedList<>();
        for (ProjectionSegment each : projectionSegments) {
            if (each instanceof ColumnProjectionSegment && each.isVisible()) {
                result.add(((ColumnProjectionSegment) each).getColumn());
            }
        }
        return result;
    }
    
    @SneakyThrows
    private UpdateStatement bindMergeUpdate(final UpdateStatement sqlStatement, final SimpleTableSegment tableSegment, final SQLStatementBinderContext statementBinderContext,
                                            final Map<String, TableSegmentBinderContext> targetTableBinderContexts, final Map<String, TableSegmentBinderContext> sourceTableBinderContexts) {
        UpdateStatement result = sqlStatement.getClass().getDeclaredConstructor().newInstance();
        result.setTable(tableSegment);
        Collection<ColumnAssignmentSegment> assignments = new LinkedList<>();
        SQLStatementBinderContext updateStatementBinderContext = new SQLStatementBinderContext(statementBinderContext.getMetaData(), statementBinderContext.getCurrentDatabaseName(),
                statementBinderContext.getDatabaseType(), statementBinderContext.getVariableNames());
        updateStatementBinderContext.getExternalTableBinderContexts().putAll(statementBinderContext.getExternalTableBinderContexts());
        updateStatementBinderContext.getExternalTableBinderContexts().putAll(sourceTableBinderContexts);
        Map<ParameterMarkerSegment, ColumnSegmentBoundedInfo> parameterMarkerSegmentBoundedInfos = new LinkedHashMap<>(sqlStatement.getSetAssignment().getAssignments().size(), 1F);
        for (ColumnAssignmentSegment each : sqlStatement.getSetAssignment().getAssignments()) {
            List<ColumnSegment> columnSegments = new ArrayList<>(each.getColumns().size());
            each.getColumns().forEach(column -> columnSegments.add(
                    ColumnSegmentBinder.bind(column, SegmentType.SET_ASSIGNMENT, updateStatementBinderContext, targetTableBinderContexts, Collections.emptyMap())));
            ExpressionSegment expression = ExpressionSegmentBinder.bind(each.getValue(), SegmentType.SET_ASSIGNMENT, updateStatementBinderContext, targetTableBinderContexts, Collections.emptyMap());
            ColumnAssignmentSegment columnAssignmentSegment = new ColumnAssignmentSegment(each.getStartIndex(), each.getStopIndex(), columnSegments, expression);
            assignments.add(columnAssignmentSegment);
            if (expression instanceof ParameterMarkerSegment) {
                parameterMarkerSegmentBoundedInfos.put((ParameterMarkerSegment) expression, columnAssignmentSegment.getColumns().get(0).getColumnBoundedInfo());
            }
        }
        SetAssignmentSegment setAssignmentSegment = new SetAssignmentSegment(sqlStatement.getSetAssignment().getStartIndex(), sqlStatement.getSetAssignment().getStopIndex(), assignments);
        result.setSetAssignment(setAssignmentSegment);
        sqlStatement.getWhere().ifPresent(optional -> result.setWhere(WhereSegmentBinder.bind(optional, updateStatementBinderContext, targetTableBinderContexts, Collections.emptyMap())));
        sqlStatement.getDeleteWhere().ifPresent(optional -> result.setDeleteWhere(WhereSegmentBinder.bind(optional, updateStatementBinderContext, targetTableBinderContexts, Collections.emptyMap())));
        sqlStatement.getOrderBy().ifPresent(result::setOrderBy);
        sqlStatement.getLimit().ifPresent(result::setLimit);
        sqlStatement.getWithSegment().ifPresent(result::setWithSegment);
        result.addParameterMarkerSegments(ParameterMarkerSegmentBinder.bind(sqlStatement.getParameterMarkerSegments(), parameterMarkerSegmentBoundedInfos));
        result.getCommentSegments().addAll(sqlStatement.getCommentSegments());
        return result;
    }
}
