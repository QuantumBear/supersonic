package com.tencent.supersonic.chat.query.llm.s2sql;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMSqlResp {

    private double sqlWeight;

    private List<Map<String, String>> fewShots;

}
