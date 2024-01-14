package com.tencent.supersonic.chat.core.query.plugin;

import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.collect.Lists;
import lombok.Data;
import java.util.List;

@Data
public class WebBase {

    private String url;

    private List<ParamOption> paramOptions = Lists.newArrayList();

    @JSONField(serialize = false)
    public List<ParamOption> getParams() {
        return paramOptions;
    }

}
