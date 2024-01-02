package com.tencent.supersonic.chat.api.pojo.response;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class SqlInfo {

    private String s2SQL;
    private String correctS2SQL;
    private String querySQL;

    private Map<String, String> filterFields;
    private List<String> selectFields;
}
