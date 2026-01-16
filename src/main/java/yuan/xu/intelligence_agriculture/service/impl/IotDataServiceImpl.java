package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.RedisTemplate;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.dto.SensorDataBO;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.mapper.IotSensorDataMapper;
import yuan.xu.intelligence_agriculture.service.IotDataService;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static yuan.xu.intelligence_agriculture.enums.EnvParameterType.*;
import static yuan.xu.intelligence_agriculture.key.RedisKey.DEVICE_LAST_ACTIVE_KEY;
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
    private RedisTemplate<String, Object> redisTemplate;

    // 用来展示存放对应的2s上传的数据,{1}分钟后存储到对应的数据库
    private final List<IotSensorData> iotSensorDataList = new ArrayList<>();


    /**
     * 处理从 MQTT 接收到的传感器数据
     *
     * @param payload 原始 MQTT 消息内容（JSON 格式）
     */
    @Override
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void processSensorData(String payload) {
        log.info("开始处理 MQTT 传感器数据: {}", payload);
        try {
            /// 1. 解析 JSON 数据为 DTO 对象
            if (!JSONUtil.isTypeJSON(payload)) {
                log.warn("非法数据格式，忽略处理: {}", payload);
                return;
            }
            SensorDataBO dto = JSONUtil.toBean(payload, SensorDataBO.class);
            String greenhouseEnvCode = dto.getGreenhouseEnvCode();
            List<SensorData> sensorDataList = dto.getSensorDataList();

            if (dto.getGreenhouseEnvCode() != null) {
                /// 2.保存采集的数据存储到数据库,并获取转型后的IotSensorData数据
                IotSensorData data = buildIotSensorData(greenhouseEnvCode, sensorDataList);
                
                /// 3. 批量持久化到数据库 (满30条保存一次)
                List<IotSensorData> toSave = null;
                synchronized (iotSensorDataList) {
                    iotSensorDataList.add(data);
                    if (iotSensorDataList.size() >= 30) {
                        toSave = new ArrayList<>(iotSensorDataList);
                        iotSensorDataList.clear();
                    }
                }
                
                if (toSave != null && !toSave.isEmpty()) {
                    boolean saved = this.saveBatch(toSave);
                    if (!saved) {
                        log.error("批量保存传感器数据到数据库失败!");
                    } else {
                        log.info("批量保存传感器数据成功，条数: {}", toSave.size());
                    }
                }

                // 4. 更新采集设备在线状态到 Redis
                updateSensorOnlineStatus(greenhouseEnvCode, sensorDataList);

                // 5. 触发自动"控制"设备逻辑 key:环境参数类型,value:传感器数据
                Map<Integer, SensorData> integerSensorDataMap = sensorDataList.stream()
                        .collect(Collectors.toMap(SensorData::getEnvParameterType, sensorData -> sensorData));
                sysControlDeviceService.checkAndAutoControl(data, integerSensorDataMap);

                // 6. WebSocket 实时推送采集的数据
                WebSocketSendInfo("SENSOR_DATA", greenhouseEnvCode, data);
                log.info("MQTT 传感器数据推送类型: {},环境类型:{},推送数据:{}", "SENSOR_DATA",
                        dto.getGreenhouseEnvCode(), data);
            }

        } catch (Exception e) {
            log.error("处理传感器数据时发生异常", e);
        }
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
     * 更新采集设备在线状态到 Redis
     */
    private void updateSensorOnlineStatus(
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
                .putAll(DEVICE_LAST_ACTIVE_KEY + greenhouseEnvCode, sensorStatusMaps);
        redisTemplate.expire(
                DEVICE_LAST_ACTIVE_KEY + greenhouseEnvCode,
                10,
                TimeUnit.SECONDS
        );
    }


}
