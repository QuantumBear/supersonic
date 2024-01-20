package com.tencent.supersonic.chat.core.parser.plugin;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.tencent.supersonic.chat.api.pojo.response.SqlInfo;
import com.tencent.supersonic.chat.core.parser.SemanticParser;
import com.tencent.supersonic.chat.core.query.SemanticQuery;
import com.tencent.supersonic.chat.core.pojo.ChatContext;
import com.tencent.supersonic.chat.core.pojo.QueryContext;
import com.tencent.supersonic.chat.api.pojo.SchemaElementMatch;
import com.tencent.supersonic.chat.api.pojo.SchemaElementType;
import com.tencent.supersonic.chat.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.chat.api.pojo.request.QueryFilter;
import com.tencent.supersonic.chat.api.pojo.request.QueryFilters;
import com.tencent.supersonic.chat.core.plugin.Plugin;
import com.tencent.supersonic.chat.core.plugin.PluginManager;
import com.tencent.supersonic.chat.core.plugin.PluginParseResult;
import com.tencent.supersonic.chat.core.plugin.PluginRecallResult;
import com.tencent.supersonic.chat.core.query.QueryManager;
import com.tencent.supersonic.chat.core.query.plugin.PluginSemanticQuery;
import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.pojo.ModelCluster;
import com.tencent.supersonic.common.pojo.enums.FilterOperatorEnum;
import com.tencent.supersonic.common.util.jsqlparser.FieldExpression;
import com.tencent.supersonic.common.util.jsqlparser.SqlParserSelectHelper;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * PluginParser defines the basic process and common methods for recalling plugins.
 */
public abstract class PluginParser implements SemanticParser {

    @Override
    public void parse(QueryContext queryContext, ChatContext chatContext) {
        for (SemanticQuery semanticQuery : queryContext.getCandidateQueries()) {
            if (queryContext.getQueryText().length() <= semanticQuery.getParseInfo().getScore()
                    && (QueryManager.getPluginQueryModes().contains(semanticQuery.getQueryMode()))) {
                return;
            }
        }
        if (!checkPreCondition(queryContext)) {
            return;
        }
        PluginRecallResult pluginRecallResult = recallPlugin(queryContext);
        if (pluginRecallResult == null) {
            return;
        }
        buildQuery(queryContext, pluginRecallResult);
    }

    public abstract boolean checkPreCondition(QueryContext queryContext);

    public abstract PluginRecallResult recallPlugin(QueryContext queryContext);

    public void buildQuery(QueryContext queryContext, PluginRecallResult pluginRecallResult) {
        Plugin plugin = pluginRecallResult.getPlugin();
        Set<Long> modelIds = pluginRecallResult.getModelIds();
        if (plugin.isContainsAllModel() || CollectionUtils.isEmpty(modelIds)) {
            modelIds = Sets.newHashSet(-1L);
        }
        for (Long modelId : modelIds) {
            PluginSemanticQuery pluginQuery = QueryManager.createPluginQuery(plugin.getType());
            SemanticParseInfo semanticParseInfo = buildSemanticParseInfo(modelId, plugin,
                    queryContext.getQueryFilters(), queryContext.getModelClusterMapInfo().getMatchedElements(modelId),
                    pluginRecallResult.getDistance());
            semanticParseInfo.setQueryMode(pluginQuery.getQueryMode());
            semanticParseInfo.setScore(pluginRecallResult.getScore());
            pluginQuery.setParseInfo(semanticParseInfo);
            fillSqlFields(queryContext, semanticParseInfo);
            queryContext.getCandidateQueries().add(pluginQuery);
        }
    }

    private void fillSqlFields(QueryContext queryContext, SemanticParseInfo semanticParseInfo) {
        List<SemanticQuery> candidateQueries = queryContext.getCandidateQueries();
        if (CollectionUtils.isEmpty(candidateQueries)) {
            return;
        }
        SemanticQuery sqlQuery = candidateQueries.get(0);
        if (sqlQuery.getParseInfo() != null) {
            SqlInfo sqlInfo = sqlQuery.getParseInfo().getSqlInfo();

            List<SchemaElementMatch> schemaElementMatches = sqlQuery.getParseInfo().getElementMatches();
            if (CollectionUtils.isEmpty(schemaElementMatches)) {
                return;
            }
            if (semanticParseInfo.getSqlInfo() == null) {
                semanticParseInfo.setSqlInfo(new SqlInfo());
            }
            SqlInfo targetSqlInfo = semanticParseInfo.getSqlInfo();
            targetSqlInfo.setFilterFields(getFilterFields(sqlInfo, schemaElementMatches));
            targetSqlInfo.setSelectFields(getSelectFields(sqlInfo, schemaElementMatches));
        }
    }

    private Map<String, String> getFilterFields(SqlInfo sqlInfo, List<SchemaElementMatch> schemaElementMatches) {
        Map<String, String> result = new HashMap<>();
        List<FieldExpression> fieldExpressions =
                SqlParserSelectHelper.getFilterExpression(sqlInfo.getS2SQL());
        for (FieldExpression fieldExpression : fieldExpressions) {
            String fieldName = fieldExpression.getFieldName();
            // convert field name to schema element name
            boolean findMatch = false;
            for (SchemaElementMatch schemaElementMatch : schemaElementMatches) {
                if (schemaElementMatch.getElement().getName().equals(fieldName)
                        || schemaElementMatch.getElement().getBizName().equals(fieldName)
                        || schemaElementMatch.getElement().getAlias().contains(fieldName)) {
                    fieldName = schemaElementMatch.getElement().getBizName();
                    findMatch = true;
                    break;
                }
            }
            // 只有找到对应的element才放入filterFields
            if (findMatch) {
                String fieldValue = fieldExpression.getFieldValue().toString();
                if (fieldName != null && fieldValue != null) {
                    result.put(fieldName, fieldExpression.getOperator() + " " + fieldValue);
                }
            }
        }
        return result;
    }

    private List<String> getSelectFields(SqlInfo sqlInfo, List<SchemaElementMatch> schemaElementMatches) {
        List<String> result = Lists.newArrayList();
        List<String> selectFields = SqlParserSelectHelper.getSelectFields(sqlInfo.getS2SQL());
        if (CollectionUtils.isEmpty(selectFields)) {
            return result;
        }
        String matchedSelectField = null;
        for (String selectField : selectFields) {
            // convert field name to schema element name
            boolean findMatch = false;
            for (SchemaElementMatch schemaElementMatch : schemaElementMatches) {
                if ((schemaElementMatch.getElement().getType() == SchemaElementType.DIMENSION
                        || schemaElementMatch.getElement().getType() == SchemaElementType.METRIC)
                        && schemaElementMatch.getElement().getName().equals(selectField)
                        || schemaElementMatch.getElement().getBizName().equals(selectField)
                        || schemaElementMatch.getElement().getAlias().contains(selectField)) {
                    matchedSelectField = schemaElementMatch.getElement().getBizName();
                    findMatch = true;
                    break;
                }
            }
            // 只有找到对应的element才放入selectFields
            if (findMatch) {
                result.add(matchedSelectField);
            }
        }
        return result;
    }

    protected List<Plugin> getPluginList(QueryContext queryContext) {
        return PluginManager.getPluginAgentCanSupport(queryContext);
    }

    protected SemanticParseInfo buildSemanticParseInfo(Long modelId, Plugin plugin, QueryFilters queryFilters,
            List<SchemaElementMatch> schemaElementMatches, double distance) {
        if (modelId == null && !CollectionUtils.isEmpty(plugin.getModelList())) {
            modelId = plugin.getModelList().get(0);
        }
        if (schemaElementMatches == null) {
            schemaElementMatches = Lists.newArrayList();
        }
        SemanticParseInfo semanticParseInfo = new SemanticParseInfo();
        semanticParseInfo.setElementMatches(schemaElementMatches);
        semanticParseInfo.setModel(ModelCluster.build(Sets.newHashSet(modelId)));
        Map<String, Object> properties = new HashMap<>();
        PluginParseResult pluginParseResult = new PluginParseResult();
        pluginParseResult.setPlugin(plugin);
        pluginParseResult.setQueryFilters(queryFilters);
        pluginParseResult.setDistance(distance);
        properties.put(Constants.CONTEXT, pluginParseResult);
        properties.put("type", "plugin");
        properties.put("name", plugin.getName());
        semanticParseInfo.setProperties(properties);
        semanticParseInfo.setScore(distance);
        fillSemanticParseInfo(semanticParseInfo);
        return semanticParseInfo;
    }

    private void fillSemanticParseInfo(SemanticParseInfo semanticParseInfo) {
        List<SchemaElementMatch> schemaElementMatches = semanticParseInfo.getElementMatches();
        if (CollectionUtils.isEmpty(schemaElementMatches)) {
            return;
        }
        schemaElementMatches.stream().filter(schemaElementMatch ->
                        SchemaElementType.VALUE.equals(schemaElementMatch.getElement().getType())
                                || SchemaElementType.ID.equals(schemaElementMatch.getElement().getType()))
                .forEach(schemaElementMatch -> {
                    QueryFilter queryFilter = new QueryFilter();
                    queryFilter.setValue(schemaElementMatch.getWord());
                    queryFilter.setElementID(schemaElementMatch.getElement().getId());
                    queryFilter.setName(schemaElementMatch.getElement().getName());
                    queryFilter.setOperator(FilterOperatorEnum.EQUALS);
                    queryFilter.setBizName(schemaElementMatch.getElement().getBizName());
                    semanticParseInfo.getDimensionFilters().add(queryFilter);
                });
    }
}
