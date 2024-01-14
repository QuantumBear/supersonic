package com.tencent.supersonic.chat.core.config;


import java.util.List;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class LLMParserConfig {


    @Value("${llm.parser.url:}")
    private String url;

    @Value("${query2sql.path:/query2sql}")
    private String queryToSqlPath;

    @Value("${dimension.topn:5}")
    private Integer dimensionTopN;

    @Value("${metric.topn:5}")
    private Integer metricTopN;

    @Value("${ignore.words:tenant,租户}")
    private List<String> ignoreWords;

    @Value("${all.model:false}")
    private Boolean allModel;
}
