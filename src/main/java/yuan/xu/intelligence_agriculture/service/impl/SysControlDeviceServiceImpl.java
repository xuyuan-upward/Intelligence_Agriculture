package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.enums.ControlStatus;
import yuan.xu.intelligence_agriculture.mapper.SysControlDeviceMapper;
import yuan.xu.intelligence_agriculture.mapper.SysControlLogMapper;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.model.SysControlLog;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.mqtt.MqttGateway;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
import yuan.xu.intelligence_agriculture.service.SysEnvThresholdService;
import yuan.xu.intelligence_agriculture.websocket.WebSocketServer;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static yuan.xu.intelligence_agriculture.enums.ControlStatus.OFF;
import static yuan.xu.intelligence_agriculture.enums.ControlStatus.ON;
import static yuan.xu.intelligence_agriculture.enums.EnvParameterType.*;
import static yuan.xu.intelligence_agriculture.service.impl.IotDataServiceImpl.DEVICE_LAST_ACTIVE_KEY;
import static yuan.xu.intelligence_agriculture.service.impl.SysEnvThresholdServiceImpl.ALL_ENV_THRESHOLD_KEY;

/**
 * 控制设备管理与业务实现类
 */
@Service
@Slf4j
public class SysControlDeviceServiceImpl extends ServiceImpl<SysControlDeviceMapper, SysControlDevice> implements SysControlDeviceService {

    @Autowired
    private MqttGateway mqttGateway;

    @Autowired
    private SysControlLogMapper sysControlLogMapper;

    @Autowired
    private SysEnvThresholdService sysEnvThresholdService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String AUTO_DEVICE_KEY = "iot:auto_devices";
    private static final String ALL_CONTROL_DEVICES_KEY = "iot:all_control_devices";

    @PostConstruct
    public void init() {
        // 自懂控制的
        refreshAutoDeviceCache();
//        refreshAllDeviceCache();
    }

    private void refreshAllDeviceCache() {
        List<SysControlDevice> list = this.list();
        redisTemplate.delete(ALL_CONTROL_DEVICES_KEY);
        if (!list.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(ALL_CONTROL_DEVICES_KEY, list);
            // 2. 为整个列表设置过期时间（例如：1小时）
            redisTemplate.expire(ALL_CONTROL_DEVICES_KEY, 24, TimeUnit.HOURS);
        }
        log.info("Redis 全量控制设备缓存已刷新，当前设备总数: {}", list.size());
    }

    // todo 后续可以实现某个控制设备开启自动,而不是开启自动模式后,所有设备都开启
    private void refreshAutoDeviceCache() {
        List<SysControlDevice> list = this.list(new LambdaQueryWrapper<SysControlDevice>()
                .eq(SysControlDevice::getControlMode, 1));

        redisTemplate.delete(AUTO_DEVICE_KEY);

        if (!list.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(AUTO_DEVICE_KEY, list);
            // 2. 为整个列表设置过期时间（例如：1小时）
            redisTemplate.expire(AUTO_DEVICE_KEY, 24, TimeUnit.HOURS);
        }
        log.info("Redis 自动控制设备缓存已刷新，当前自动模式设备数量: {}", list.size());
    }



    @Override
    @SuppressWarnings("unchecked")
    public List<SysControlDevice> listAllDevicesFromCache() {
        List<SysControlDevice> list = (List<SysControlDevice>) redisTemplate.opsForValue().get(ALL_CONTROL_DEVICES_KEY);
        if (list == null || list.isEmpty()) {
            refreshAllDeviceCache();
            list = this.list();
        }

        if (list != null) {
            long currentTime = System.currentTimeMillis();
            for (SysControlDevice device : list) {
                String key = DEVICE_LAST_ACTIVE_KEY + device.getDeviceCode();
                Object lastActive = redisTemplate.opsForValue().get(key);
                if (lastActive != null && (currentTime - Long.parseLong(lastActive.toString()) < 6000)) {
                    device.setOnlineStatus(1);
                } else {
                    device.setOnlineStatus(0);
                }
            }
        }
        return list;
    }


    /**
     * 下发命令
     * @param deviceId 对应的设备ID
     * @param status
     */
    @Override
    public void controlDevice(Long deviceId, Integer status) {
        SysControlDevice device = this.getById(deviceId);
        if (device == null) return;
        Map<String, Object> command = new HashMap<>();
        command.put("deviceCode", device.getDeviceCode());
        command.put("command", status == 1 ? "ON" : "OFF");
        // 发送给MQTT
        mqttGateway.sendToMqtt(JSONUtil.toJsonStr(command));

        SysControlLog logEntry = new SysControlLog();
        logEntry.setDeviceId(deviceId);
        logEntry.setOperationType(device.getControlMode() == 1 ? "AUTO" : "MANUAL");
        logEntry.setOperationDesc((device.getControlMode() == 1 ? "自动" : "手动") + (status == 1 ? "开启" : "关闭") + device.getDeviceName());
        logEntry.setCreateTime(new Date());
        sysControlLogMapper.insert(logEntry);

        // 更新数据库中的设备状态
        device.setStatus(status);
        device.setUpdateTime(new Date());
        this.updateById(device);

        if (device.getControlMode() == 1) {
            refreshAutoDeviceCache();
        }
        // todo 对应的采集设备不需要关注
//        refreshAllDeviceCache();

        Map<String, Object> deviceUpdateMsg = new HashMap<>();
        deviceUpdateMsg.put("type", "CONTROL_DEVICE_STATUS");
        Map<String, Object> deviceData = new HashMap<>();
        deviceData.put("deviceCode", device.getDeviceCode());
        deviceData.put("status", status);
        deviceUpdateMsg.put("data", deviceData);
        WebSocketServer.sendInfo(JSONUtil.toJsonStr(deviceUpdateMsg));

        Map<String, Object> logUpdateMsg = new HashMap<>();
        logUpdateMsg.put("type", "SYSTEM_LOG");
        Map<String, Object> logData = new HashMap<>();
        logData.put("type", device.getControlMode() == 1 ? "success" : "primary");
        logData.put("message", logEntry.getOperationDesc());
        logData.put("source", device.getControlMode() == 1 ? "自动控制" : "手动控制");
        logUpdateMsg.put("data", logData);
        WebSocketServer.sendInfo(JSONUtil.toJsonStr(logUpdateMsg));
    }

    @Override
    public void updateMode(Long deviceId, Integer mode, BigDecimal min, BigDecimal max) {
        SysControlDevice device = this.getById(deviceId);
        if (device == null) return;
        device.setControlMode(mode);
        this.updateById(device);

        // 如果有关联的阈值 ID，且提供了 min/max，则更新阈值表
        if (device.getEnvThresholdId() != null && (min != null || max != null)) {
            SysEnvThreshold threshold = sysEnvThresholdService.getById(device.getEnvThresholdId());
            if (threshold != null) {
                if (min != null) threshold.setMinValue(min);
                if (max != null) threshold.setMaxValue(max);
                sysEnvThresholdService.updateById(threshold);
            }
        }

        refreshAutoDeviceCache();
        refreshAllDeviceCache();

        log.info("更新设备模式并同步 Redis 完成: 设备={}, 模式={}, 阈值范围=[{}, {}]", 
                device.getDeviceName(), mode == 1 ? "自动" : "手动", min, max);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void checkAndAutoControl(IotSensorData data, Map<Integer, SensorData> integerSensorDataMap) {
        List<SysControlDevice> autoDeviceCache = (List<SysControlDevice>) redisTemplate.opsForValue().get(AUTO_DEVICE_KEY);
        List<SysEnvThreshold> sysEnvThresholdList = (List<SysEnvThreshold>) redisTemplate.opsForValue().get(ALL_ENV_THRESHOLD_KEY);

        if (autoDeviceCache == null || autoDeviceCache.isEmpty() || sysEnvThresholdList == null) {
            return;
        }

        // 转换为 Map<阈值ID, 阈值对象>
        Map<Long, SysEnvThreshold> envThresholdMap = sysEnvThresholdList.stream()
                .collect(Collectors.toMap(SysEnvThreshold::getId, t -> t, (e, r) -> e));

        for (SysControlDevice sysControlDevice : autoDeviceCache) {
            SysEnvThreshold threshold = envThresholdMap.get(sysControlDevice.getEnvThresholdId());
            if (threshold == null) continue;

            // 获取当前传感器数值
            SensorData sensorData = integerSensorDataMap.get(threshold.getEnvParameterType());
            if (sensorData == null || sensorData.getData() == null) continue;

            BigDecimal currentVal = sensorData.getData();
            BigDecimal min = threshold.getMinValue();
            BigDecimal max = threshold.getMaxValue();
            if (min == null || max == null) continue;

            Integer typeCode = threshold.getEnvParameterType();
            boolean shouldOpen = false;
            boolean shouldClose = false;

            // 1. 低开高关类逻辑（加热、补光）：小于下限开启，大于下限关闭
            if (AIR_TEMP.getEnvParameterType().equals(typeCode) || 
                SOIL_TEMP.getEnvParameterType().equals(typeCode) || 
                LIGHT_INTENSITY.getEnvParameterType().equals(typeCode)) {
                if (currentVal.compareTo(min) < 0) shouldOpen = true;
                else if (currentVal.compareTo(min) > 0) shouldClose = true;
            } 
            // 2. 高开低关类逻辑（排风、降碳）：大于上限开启，小于上限关闭
            else if (AIR_HUMIDITY.getEnvParameterType().equals(typeCode) || 
                     SOIL_HUMIDITY.getEnvParameterType().equals(typeCode) || 
                     CO2_CONCENTRATION.getEnvParameterType().equals(typeCode)) {
                if (currentVal.compareTo(max) > 0) shouldOpen = true;
                else if (currentVal.compareTo(max) < 0) shouldClose = true;
            }

            // 执行开关动作（状态发生变化才执行）
            if (shouldOpen && OFF.getCode().equals(sysControlDevice.getStatus())) {
                log.info("【自动控制触发】指标类型: {}, 当前值: {}, 开启设备: {}",
                        typeCode, currentVal, sysControlDevice.getDeviceName());
                controlDevice(sysControlDevice.getId(), ON.getCode());
            } else if (shouldClose && ON.getCode().equals(sysControlDevice.getStatus())) {
                log.info("【自动控制触发】指标类型: {}, 当前值: {}, 关闭设备: {}",
                        typeCode, currentVal, sysControlDevice.getDeviceName());
                controlDevice(sysControlDevice.getId(), OFF.getCode());
            }
        }
    }

}
