package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.req.AnalysisReq;
import yuan.xu.intelligence_agriculture.resp.IotSensorHistoryDataResp;

import java.util.List;

public interface IotDataService extends IService<IotSensorData> {
    /**
     * 处理从 MQTT 接收到的传感器数据
     *
     * @param payload 原始 MQTT 消息内容（JSON 格式）
     * @param topic   消息来源的 Topic
     */
    void processSensorData(String payload, String topic);

    /**
     * 获取历史工况分析数据
     * 限制：只能查询 6 小时之前的数据
     */
    CommonResult<List<IotSensorHistoryDataResp>> getAnalysisData(AnalysisReq req);

    /**
     * 获取近一小时的记录，用于趋势图表展示
     */
    List<IotSensorHistoryDataResp> getHistoryData(String envCode);
}
