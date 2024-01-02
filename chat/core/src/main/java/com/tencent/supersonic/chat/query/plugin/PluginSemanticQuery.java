package com.tencent.supersonic.chat.query.plugin;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.chat.api.pojo.SchemaElementMatch;
import com.tencent.supersonic.chat.api.pojo.SchemaElementType;
import com.tencent.supersonic.chat.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.chat.api.pojo.request.QueryFilter;
import com.tencent.supersonic.chat.api.pojo.request.QueryFilters;
import com.tencent.supersonic.chat.api.pojo.request.QueryReq;
import com.tencent.supersonic.chat.api.pojo.response.SqlInfo;
import com.tencent.supersonic.chat.plugin.PluginParseResult;
import com.tencent.supersonic.chat.query.BaseSemanticQuery;
import com.tencent.supersonic.chat.query.plugin.ParamOption.ParamType;
import com.tencent.supersonic.common.pojo.DateConf;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class PluginSemanticQuery extends BaseSemanticQuery {

    @Override
    public String explain(User user) {
        return null;
    }

    @Override
    public void initS2Sql(User user) {

    }

    private Map<Long, Object> getFilterMap(PluginParseResult pluginParseResult) {
        Map<Long, Object> map = new HashMap<>();
        QueryReq queryReq = pluginParseResult.getRequest();
        if (queryReq == null || queryReq.getQueryFilters() == null) {
            return map;
        }
        QueryFilters queryFilters = queryReq.getQueryFilters();
        List<QueryFilter> queryFilterList = queryFilters.getFilters();
        if (CollectionUtils.isEmpty(queryFilterList)) {
            return map;
        }
        for (QueryFilter queryFilter : queryFilterList) {
            map.put(queryFilter.getElementID(), queryFilter.getValue());
        }
        return map;
    }

    protected Map<String, Object> getElementMap(PluginParseResult pluginParseResult) {
        Map<String, Object> elementValueMap = new HashMap<>();
        Map<Long, Object> filterValueMap = getFilterMap(pluginParseResult);
        List<SchemaElementMatch> schemaElementMatchList = parseInfo.getElementMatches();
        if (!CollectionUtils.isEmpty(schemaElementMatchList)) {
            schemaElementMatchList.stream().filter(schemaElementMatch ->
                    SchemaElementType.VALUE.equals(schemaElementMatch.getElement().getType())
                        || SchemaElementType.ID.equals(schemaElementMatch.getElement().getType()))
                    .filter(schemaElementMatch -> schemaElementMatch.getSimilarity() == 1.0)
                    .forEach(schemaElementMatch -> {
                        Object queryFilterValue = filterValueMap.get(schemaElementMatch.getElement().getId());
                        if (queryFilterValue != null) {
                            if (String.valueOf(queryFilterValue).equals(String.valueOf(schemaElementMatch.getWord()))) {
                                elementValueMap.put(
                                        String.valueOf(schemaElementMatch.getElement().getId()),
                                        schemaElementMatch.getWord());
                            }
                        } else {
                            elementValueMap.computeIfAbsent(
                                    String.valueOf(schemaElementMatch.getElement().getId()),
                                    k -> schemaElementMatch.getWord());
                        }
                    });
        }
        return elementValueMap;
    }

    protected WebBase fillWebBaseResult(WebBase webPage, User user,
            PluginParseResult pluginParseResult) {
        WebBase webBaseResult = new WebBase();
        webBaseResult.setUrl(webPage.getUrl());
        Map<String, Object> elementValueMap = getElementMap(pluginParseResult);
        List<ParamOption> paramOptions = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(webPage.getParamOptions()) && !CollectionUtils.isEmpty(elementValueMap)) {
            for (ParamOption paramOption : webPage.getParamOptions()) {
                if (paramOption.getModelId() != null
                        && !parseInfo.getModel().getModelIds().contains(paramOption.getModelId())) {
                    continue;
                }
                if (!ParamOption.ParamType.SEMANTIC.equals(paramOption.getParamType())) {
                    continue;
                }
                String elementId = String.valueOf(paramOption.getElementId());
                Object elementValue = elementValueMap.get(elementId);
                if (elementValue != null) {
                    paramOption.setValue(elementValue);
                    paramOptions.add(paramOption);
                }
            }
        }
        // 日期处理，如果有日期，需要加上日期参数
        Optional<DateConf> dateConfOptional = getDateConf();
        if (dateConfOptional.isPresent()) {
            DateConf dateConf = dateConfOptional.get();
            ParamOption paramOption = new ParamOption();
            paramOption.setParamType(ParamType.CUSTOM);
            paramOption.setKey("start_date");
            paramOption.setValue(dateConf.getStartDate());
            paramOptions.add(paramOption);

            paramOption = new ParamOption();
            paramOption.setParamType(ParamType.CUSTOM);
            paramOption.setKey("end_date");
            paramOption.setValue(dateConf.getEndDate());
            paramOptions.add(paramOption);
        }
        // 租户处理，如果有租户，需要加上租户参数
        if (user.getTenantId() != null && user.getTenantId() > 0) {
            ParamOption paramOption = new ParamOption();
            paramOption.setParamType(ParamType.CUSTOM);
            paramOption.setKey("tenant_id");
            paramOption.setValue(user.getTenantId());
            paramOptions.add(paramOption);
        }
        // filterFieldsMap处理，如果有filterFieldsMap，需要加上filterFieldsMap参数
        Optional<Map<String, String>> filterFieldsMapOptional = getFilterFieldsMap();
        if (filterFieldsMapOptional.isPresent()) {
            Map<String, String> filterFieldsMap = filterFieldsMapOptional.get();
            ParamOption paramOption = new ParamOption();
            paramOption.setParamType(ParamType.CUSTOM);
            paramOption.setKey("filter_fields_map");
            paramOption.setValue(JSON.toJSONString(filterFieldsMap));
        }
        // selectFields处理，如果有selectFields，需要加上selectFields参数
        Optional<List<String>> selectFieldsOptional = getSelectFields();
        if (selectFieldsOptional.isPresent()) {
            List<String> selectFields = selectFieldsOptional.get();
            ParamOption paramOption = new ParamOption();
            paramOption.setParamType(ParamType.CUSTOM);
            paramOption.setKey("select_fields");
            paramOption.setValue(JSON.toJSONString(selectFields));
        }
        webBaseResult.setParamOptions(paramOptions);
        return webBaseResult;
    }

    protected Optional<DateConf> getDateConf() {
        SemanticParseInfo parseInfo = super.getParseInfo();
        return Optional.ofNullable(parseInfo).map(SemanticParseInfo::getDateInfo);
    }

    protected Optional<Map<String, String>> getFilterFieldsMap() {
        SemanticParseInfo parseInfo = super.getParseInfo();
        SqlInfo sqlInfo = parseInfo.getSqlInfo();
        return Optional.ofNullable(sqlInfo).map(SqlInfo::getFilterFields);
    }

    protected Optional<List<String>> getSelectFields() {
        SemanticParseInfo parseInfo = super.getParseInfo();
        SqlInfo sqlInfo = parseInfo.getSqlInfo();
        return Optional.ofNullable(sqlInfo).map(SqlInfo::getSelectFields);
    }

}
