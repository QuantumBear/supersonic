package com.tencent.supersonic.chat.corrector;

import com.google.common.collect.Lists;
import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.chat.api.pojo.SchemaElement;
import com.tencent.supersonic.chat.api.pojo.SchemaValueMap;
import com.tencent.supersonic.chat.api.pojo.SemanticParseInfo;
import com.tencent.supersonic.chat.api.pojo.SemanticSchema;
import com.tencent.supersonic.chat.api.pojo.request.QueryFilter;
import com.tencent.supersonic.chat.api.pojo.request.QueryFilters;
import com.tencent.supersonic.chat.api.pojo.request.QueryReq;
import com.tencent.supersonic.chat.parser.sql.llm.S2SqlDateHelper;
import com.tencent.supersonic.common.pojo.Constants;
import com.tencent.supersonic.common.pojo.enums.FilterOperatorEnum;
import com.tencent.supersonic.common.pojo.enums.TimeDimensionEnum;
import com.tencent.supersonic.common.util.ContextUtils;
import com.tencent.supersonic.common.util.StringUtil;
import com.tencent.supersonic.common.util.jsqlparser.SqlParserAddHelper;
import com.tencent.supersonic.common.util.jsqlparser.SqlParserReplaceHelper;
import com.tencent.supersonic.common.util.jsqlparser.SqlParserSelectHelper;
import com.tencent.supersonic.headless.api.response.DimensionResp;
import com.tencent.supersonic.headless.server.pojo.MetaFilter;
import com.tencent.supersonic.headless.server.service.DimensionService;
import com.tencent.supersonic.knowledge.service.SchemaService;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Perform SQL corrections on the "Where" section in S2SQL.
 */
@Slf4j
public class WhereCorrector extends BaseSemanticCorrector {

    @Override
    public void doCorrect(QueryReq queryReq, SemanticParseInfo semanticParseInfo) {

        addDateIfNotExist(semanticParseInfo);

        parserDateDiffFunction(semanticParseInfo);

        addQueryFilter(queryReq, semanticParseInfo);

        updateFieldValueByTechName(semanticParseInfo);
    }

    private void addQueryFilter(QueryReq queryReq, SemanticParseInfo semanticParseInfo) {
        QueryFilter tenantQueryFilter = getTenantDefaultFilters(queryReq, semanticParseInfo);
        if (Objects.nonNull(tenantQueryFilter)) {
            if (queryReq.getQueryFilters() == null) {
                queryReq.setQueryFilters(new QueryFilters());
            }
            queryReq.getQueryFilters().getFilters().add(tenantQueryFilter);
        }
        String queryFilter = getQueryFilter(queryReq.getQueryFilters());

        String correctS2SQL = semanticParseInfo.getSqlInfo().getCorrectS2SQL();

        if (StringUtils.isNotEmpty(queryFilter)) {
            log.info("add queryFilter to correctS2SQL :{}", queryFilter);
            Expression expression = null;
            try {
                expression = CCJSqlParserUtil.parseCondExpression(queryFilter);
            } catch (JSQLParserException e) {
                log.error("parseCondExpression", e);
            }
            correctS2SQL = SqlParserAddHelper.addWhere(correctS2SQL, expression);
            semanticParseInfo.getSqlInfo().setCorrectS2SQL(correctS2SQL);
        }
    }

    private void parserDateDiffFunction(SemanticParseInfo semanticParseInfo) {
        String correctS2SQL = semanticParseInfo.getSqlInfo().getCorrectS2SQL();
        correctS2SQL = SqlParserReplaceHelper.replaceFunction(correctS2SQL);
        semanticParseInfo.getSqlInfo().setCorrectS2SQL(correctS2SQL);
    }

    private void addDateIfNotExist(SemanticParseInfo semanticParseInfo) {
        String correctS2SQL = semanticParseInfo.getSqlInfo().getCorrectS2SQL();
        List<String> whereFields = SqlParserSelectHelper.getWhereFields(correctS2SQL);
        if (CollectionUtils.isEmpty(whereFields)
                || !TimeDimensionEnum.containsZhTimeDimension(whereFields)) {
            String currentDate = S2SqlDateHelper.getReferenceDate(semanticParseInfo.getModelId());
            if (StringUtils.isNotBlank(currentDate)) {
                correctS2SQL = SqlParserAddHelper.addParenthesisToWhere(correctS2SQL);
                correctS2SQL = SqlParserAddHelper.addWhere(
                    correctS2SQL, TimeDimensionEnum.DAY.getChName(), currentDate);
            }
        }
        semanticParseInfo.getSqlInfo().setCorrectS2SQL(correctS2SQL);
    }

    private QueryFilter getTenantDefaultFilters(QueryReq queryReq,
            SemanticParseInfo semanticParseInfo) {
        User user = queryReq.getUser();
        if (Objects.isNull(user)) {
            return null;
        }
        Long tenantId = user.getTenantId();
        if (tenantId == null || tenantId == 0 || tenantId == -1) {
            return null;
        }
        Long modelId = semanticParseInfo.getModelId();
        DimensionService dimensionService = ContextUtils.getBean(DimensionService.class);
        List<DimensionResp> modelCluster = dimensionService.getDimensions(
            new MetaFilter(Lists.newArrayList(modelId)));
        Map<String, DimensionResp> dimensionRespMap = modelCluster.stream()
                .collect(Collectors.groupingBy(DimensionResp::getBizName,
                Collectors.collectingAndThen(Collectors.toList(), value -> value.get(0))));

        DimensionResp tenantIdDim = dimensionRespMap.get(Constants.TENANT_BIZ_NAME);

        return getFilter(
                Constants.TENANT_BIZ_NAME,
                FilterOperatorEnum.EQUALS, tenantId, tenantIdDim.getName(), tenantIdDim.getId());
    }

    private String getQueryFilter(QueryFilters queryFilters) {
        if (Objects.isNull(queryFilters) || CollectionUtils.isEmpty(queryFilters.getFilters())) {
            return null;
        }
        return queryFilters.getFilters().stream()
            .map(filter -> {
                String bizNameWrap = StringUtil.getSpaceWrap(filter.getName());
                String operatorWrap = StringUtil.getSpaceWrap(filter.getOperator().getValue());
                String valueWrap = StringUtil.getCommaWrap(filter.getValue().toString());
                return bizNameWrap + operatorWrap + valueWrap;
            })
            .collect(Collectors.joining(Constants.AND_UPPER));
    }

    private void updateFieldValueByTechName(SemanticParseInfo semanticParseInfo) {
        SemanticSchema semanticSchema = ContextUtils.getBean(SchemaService.class)
                .getSemanticSchema();
        Set<Long> modelIds = semanticParseInfo.getModel().getModelIds();
        List<SchemaElement> dimensions = semanticSchema.getDimensions(modelIds);

        if (CollectionUtils.isEmpty(dimensions)) {
            return;
        }

        Map<String, Map<String, String>> aliasAndBizNameToTechName = getAliasAndBizNameToTechName(
                dimensions);
        String correctS2SQL = SqlParserReplaceHelper.replaceValue(
                semanticParseInfo.getSqlInfo().getCorrectS2SQL(),
                aliasAndBizNameToTechName);
        semanticParseInfo.getSqlInfo().setCorrectS2SQL(correctS2SQL);
    }

    private Map<String, Map<String, String>> getAliasAndBizNameToTechName(
            List<SchemaElement> dimensions) {
        if (CollectionUtils.isEmpty(dimensions)) {
            return new HashMap<>();
        }

        Map<String, Map<String, String>> result = new HashMap<>();

        for (SchemaElement dimension : dimensions) {
            if (Objects.isNull(dimension)
                    || Strings.isEmpty(dimension.getName())
                    || CollectionUtils.isEmpty(dimension.getSchemaValueMaps())) {
                continue;
            }
            String name = dimension.getName();

            Map<String, String> aliasAndBizNameToTechName = new HashMap<>();

            for (SchemaValueMap valueMap : dimension.getSchemaValueMaps()) {
                if (Objects.isNull(valueMap) || Strings.isEmpty(valueMap.getTechName())) {
                    continue;
                }
                if (Strings.isNotEmpty(valueMap.getBizName())) {
                    aliasAndBizNameToTechName.put(valueMap.getBizName(), valueMap.getTechName());
                }
                if (!CollectionUtils.isEmpty(valueMap.getAlias())) {
                    valueMap.getAlias().stream().forEach(alias -> {
                        if (Strings.isNotEmpty(alias)) {
                            aliasAndBizNameToTechName.put(alias, valueMap.getTechName());
                        }
                    });
                }
            }
            if (!CollectionUtils.isEmpty(aliasAndBizNameToTechName)) {
                result.put(name, aliasAndBizNameToTechName);
            }
        }
        return result;
    }

    public static QueryFilter getFilter(String bizName, FilterOperatorEnum filterOperatorEnum,
            Object value, String name, Long elementId) {
        QueryFilter filter = new QueryFilter();
        filter.setBizName(bizName);
        filter.setOperator(filterOperatorEnum);
        filter.setValue(value);
        filter.setName(name);
        filter.setElementID(elementId);
        return filter;
    }
}