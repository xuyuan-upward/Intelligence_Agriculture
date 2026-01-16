package yuan.xu.intelligence_agriculture.req;

import lombok.Data;

import java.util.List;

@Data
public class EnvThresholdListReq  {
    /**
     * 环境编码(温室/区域)
     */
    private String envCode;

    /**
     * 列表环境参数类型 (1-6)
     */
    private List<EnvThresholdReq> EnvThresholdList;

}
