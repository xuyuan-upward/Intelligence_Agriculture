package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.dto.SensorDataBO;
import yuan.xu.intelligence_agriculture.mapper.IotSensorDataMapper;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.req.AnalysisReq;
import yuan.xu.intelligence_agriculture.resp.IotSensorDataListResp;
import yuan.xu.intelligence_agriculture.resp.IotSensorDataResp;
import yuan.xu.intelligence_agriculture.resp.IotSensorHistoryDataResp;
import yuan.xu.intelligence_agriculture.service.IotDataService;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
import yuan.xu.intelligence_agriculture.service.SysEnvThresholdService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static yuan.xu.intelligence_agriculture.enums.EnvParameterType.*;
import static yuan.xu.intelligence_agriculture.key.RedisKey.DEVICE_LAST_TIME_KEY;
import static yuan.xu.intelligence_agriculture.key.RedisKey.ENV_THRESHOLD_KEY;
import static yuan.xu.intelligence_agriculture.websocket.WebSocketServer.WebSocketSendInfo;

/**
 * 传感器数据处理业务实现类
 * 负责接收、解析、校验 MQTT 原始数据，并持久化到数据库及实时推送至 WebSocket 客户端
 */
@Service
@Slf4j
public class IotDataServiceImpl extends ServiceImpl<IotSensorDataMapper, IotSensorData> implements IotDataService {

    @Autowired
    private SysControlDeviceService sysControlDeviceService;
    @Autowired
    private SysEnvThresholdService sysEnvThresholdService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 用来展示存放对应的2s上传的数据,{1}分钟后存储到对应的数据库
    private final List<IotSensorData> iotSensorDataList = new ArrayList<>();
    List<IotSensorData> toSave = null;

    /**
     * 传感器数据缓冲队列
     * 线程安全，用于削峰填谷
     */
    private final BlockingQueue<IotSensorData> sensorDataQueue =
            new LinkedBlockingQueue<>(10_000); // 最大容量，防止 OOM


    /**
     * 处理从 MQTT 接收到的传感器数据
     *
     * @param payload 原始 MQTT 消息内容（JSON 格式）
     * @param topic   消息来源的 Topic
     */
    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void processSensorData(String payload, String topic) {
        log.info("接收并处理 MQTT 传感器数据, Topic: {}, Payload: {}", topic, payload);
        try {
            /// 1. 解析 JSON 数据为 DTO 对象
            if (!JSONUtil.isTypeJSON(payload)) {
                log.warn("非法数据格式，忽略处理: {}", payload);
                return;
            }
            SensorDataBO sensorDataBO = JSONUtil.toBean(payload, SensorDataBO.class);
            String greenhouseEnvCode = sensorDataBO.getGreenhouseEnvCode();
            List<SensorData> sensorDataList = sensorDataBO.getSensorDataList();

            if (greenhouseEnvCode != null) {
                /// 2. 更新采集设备最新上报时间戳到 Redis
                updateSensorLatestReportTime(greenhouseEnvCode, sensorDataList);
                /// 3.保存采集的数据存储到数据库,并获取转型后的IotSensorData数据
                IotSensorData data = buildIotSensorData(greenhouseEnvCode, sensorDataList);

                /// 4. 批量持久化到数据库 (每隔30s保存)
                // 4. 放入队列（不阻塞）
                boolean success = sensorDataQueue.offer(data);
                if (!success) {
                    log.error("传感器数据队列已满，丢弃数据: {}", data);
                }


                /// 5. 触发自动"控制"设备逻辑 key:环境参数类型,value:传感器数据
                Map<Integer, SensorData> integerSensorDataMap = sensorDataList.stream()
                        .collect(Collectors.toMap(SensorData::getEnvParameterType, sensorData -> sensorData));

                /// 6. 检查是否触发对应的自动控制，以及对应的控制设备是否需要"开启"，以及对应的当前采集的数据是否处于异常
                sysControlDeviceService.checkAndAutoControl(data, integerSensorDataMap);

                /// 7. 构建 List<IotSensorDataListResp>
                IotSensorDataListResp iotSensorDataListResp = buildIotSensorDataListResp(data, sensorDataList, sensorDataBO);

                /// 8. WebSocket 实时推送采集的数据
                WebSocketSendInfo("SENSOR_DATA", greenhouseEnvCode, iotSensorDataListResp);
            }

        } catch (Exception e) {
            log.error("处理传感器数据时发生异常", e);
        }
    }

    /**
     * 定时批量入库
     * 每 30 秒执行一次，最多保存 30 条
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveSensorData() {

        List<IotSensorData> batchList = new ArrayList<>(30);

        // 一次最多取 30 条
        sensorDataQueue.drainTo(batchList, 30);

        if (CollUtil.isEmpty(batchList)) {
            return;
        }

        boolean saved = this.saveBatch(batchList);
        if (!saved) {
            log.error("批量保存传感器数据失败，条数: {}", batchList.size());
            throw new RuntimeException("批量入库失败");
        }

        log.info("批量保存传感器数据成功，条数: {}", batchList.size());
    }


    @Override
    public CommonResult<List<IotSensorHistoryDataResp>> getAnalysisData(AnalysisReq req) {
        if (req.getEnvCode() == null || req.getStartTime() == null || req.getEndTime() == null) {
            return CommonResult.failed("请求参数不完整");
        }

        long startTime = req.getStartTime().getTime();
        long endTime = req.getEndTime().getTime();
        long now = System.currentTimeMillis();
        long sixHoursMs = 6 * 60 * 60 * 1000;

        // 1. 校验查询范围是否超过 6 小时
        if (endTime - startTime > sixHoursMs) {
            return CommonResult.failed("查询时间范围不能超过 6 小时");
        }

        // 2. 校验结束时间是否在未来 (允许查询最近的数据)
        if (endTime > now + 60 * 1000) { // 稍微给点容错
            return CommonResult.failed("不能查询未来的数据");
        }

        // 3. 校验开始时间是否早于允许的最早时间 (最近 6 小时)
        if (startTime < now - sixHoursMs - 60 * 1000) { // 稍微给点容错
            return CommonResult.failed("只能查询最近 6 小时内的数据");
        }

        // 4. 校验开始时间是否早于结束时间
        if (startTime >= endTime) {
            return CommonResult.failed("开始时间必须早于结束时间");
        }

        List<IotSensorData> list = this.lambdaQuery()
                .eq(IotSensorData::getGreenhouseEnvCode, req.getEnvCode())
                .ge(IotSensorData::getCreateTime, req.getStartTime())
                .le(IotSensorData::getCreateTime, req.getEndTime())
                .orderByAsc(IotSensorData::getCreateTime)
                .list();

        List<IotSensorHistoryDataResp> respList = new ArrayList<>();
        for (IotSensorData data : list) {
            IotSensorHistoryDataResp item = new IotSensorHistoryDataResp();
            BeanUtils.copyProperties(data, item);
            respList.add(item);
        }
        return CommonResult.success(respList);
    }

    @Override
    public List<IotSensorHistoryDataResp> getHistoryData(String envCode) {
        // 获取一小时前的时间点
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<IotSensorData> list = this.lambdaQuery()
                .eq(IotSensorData::getGreenhouseEnvCode, envCode)
                .ge(IotSensorData::getCreateTime, oneHourAgo)
                .orderByDesc(IotSensorData::getCreateTime)
                .list();
        List<IotSensorHistoryDataResp> respList = new ArrayList<>();
        for (IotSensorData data : list) {
            IotSensorHistoryDataResp item = new IotSensorHistoryDataResp();
            BeanUtils.copyProperties(data, item);
            respList.add(item);
        }
        return respList;
    }

    private IotSensorDataListResp buildIotSensorDataListResp(IotSensorData data, List<SensorData> sensorDataList, SensorDataBO sensorDataBO) {
        IotSensorDataListResp iotSensorDataListResp = new IotSensorDataListResp();
        List<IotSensorDataResp> iotSensorDataRespList = new ArrayList<>();
        // 转换对应的key，value结构
        Map<Integer, SysEnvThreshold> sysEnvThresholdHashMap = new HashMap<>();
        sysEnvThresholdService.FromCacheGetEnvThreshold(data.getGreenhouseEnvCode()).forEach((id, threshold) -> {
            sysEnvThresholdHashMap.put(threshold.getEnvParameterType(), threshold);
        });
        sensorDataList.forEach(
                sensorData -> {
                    IotSensorDataResp iotSensorDataResp = new IotSensorDataResp();
                    SysEnvThreshold sysEnvThreshold = sysEnvThresholdHashMap.get(sensorData.getEnvParameterType());
                    BigDecimal min = sysEnvThreshold.getMinValue();
                    BigDecimal max = sysEnvThreshold.getMaxValue();
                    BigDecimal currentVal = sensorData.getData();
                    Integer envParameterType = sensorData.getEnvParameterType();
                    iotSensorDataResp.setDataValue(currentVal);
                    iotSensorDataResp.setEnvParameterType(sensorData.getEnvParameterType());
                    // 低开高关（加热、补光）
                    if (AIR_TEMP.getEnvParameterType().equals(envParameterType)
                            || SOIL_TEMP.getEnvParameterType().equals(envParameterType)
                            || LIGHT_INTENSITY.getEnvParameterType().equals(envParameterType) || AIR_HUMIDITY.getEnvParameterType().equals(envParameterType)
                            || SOIL_HUMIDITY.getEnvParameterType().equals(envParameterType)) {
                        if (currentVal.compareTo(min) < 0) iotSensorDataResp.setEx(1);
                        else if (currentVal.compareTo(min) > 0) iotSensorDataResp.setEx(0);
                    }
                    // 高开低关（排风、降碳,补水）
                    else if (
                            CO2_CONCENTRATION.getEnvParameterType().equals(envParameterType)) {

                        if (currentVal.compareTo(max) > 0) iotSensorDataResp.setEx(1);
                        else if (currentVal.compareTo(max) < 0) iotSensorDataResp.setEx(0);
                    }
                    iotSensorDataRespList.add(iotSensorDataResp);
                }
        );
        iotSensorDataListResp.setIotSensorDataRespList(iotSensorDataRespList);
        iotSensorDataListResp.setEnvCode(sensorDataBO.getGreenhouseEnvCode());
        return iotSensorDataListResp;
    }

    private IotSensorData buildIotSensorData(String envCode, List<SensorData> sensorDataList) {
        IotSensorData data = new IotSensorData();
        data.setGreenhouseEnvCode(envCode);
        data.setCreateTime(new Date());

        for (SensorData sensorData : sensorDataList) {
            Integer type = sensorData.getEnvParameterType();
            BigDecimal value = sensorData.getData();
            if (type == null || value == null) {
                continue;
            }

            if (AIR_TEMP.getEnvParameterType().equals(type)) {
                data.setAirTemp(value);
            } else if (AIR_HUMIDITY.getEnvParameterType().equals(type)) {
                data.setAirHumidity(value);
            } else if (SOIL_TEMP.getEnvParameterType().equals(type)) {
                data.setSoilTemp(value);
            } else if (SOIL_HUMIDITY.getEnvParameterType().equals(type)) {
                data.setSoilHumidity(value);
            } else if (CO2_CONCENTRATION.getEnvParameterType().equals(type)) {
                data.setCo2Concentration(value);
            } else if (LIGHT_INTENSITY.getEnvParameterType().equals(type)) {
                data.setLightIntensity(value);
            }
        }
        return data;
    }

    /**
     * 更新采集设备最新上报时间戳到 Redis
     */
    private void updateSensorLatestReportTime(
            String greenhouseEnvCode,
            List<SensorData> sensorDataList
    ) {
        if (greenhouseEnvCode == null || sensorDataList == null || sensorDataList.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        HashMap<String, Object> sensorStatusMaps = new HashMap<>();

        for (SensorData sensorData : sensorDataList) {
            String deviceCode = sensorData.getDeviceCode();
            if (deviceCode != null) {
                sensorStatusMaps.put(deviceCode, now);
            }
        }

        redisTemplate.opsForHash()
                .putAll(DEVICE_LAST_TIME_KEY + greenhouseEnvCode, sensorStatusMaps);
        // todo 先不设置过期时间
//        redisTemplate.expire(
//                DEVICE_LAST_ACTIVE_KEY + greenhouseEnvCode,
//                10,
//                TimeUnit.SECONDS
//        );
    }


}
