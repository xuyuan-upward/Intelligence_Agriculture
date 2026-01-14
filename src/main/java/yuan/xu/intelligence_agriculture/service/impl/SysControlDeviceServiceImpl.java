package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
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
        refreshAutoDeviceCache();
        refreshAllDeviceCache();
    }

    private void refreshAllDeviceCache() {
        List<SysControlDevice> list = this.list();
        redisTemplate.delete(ALL_CONTROL_DEVICES_KEY);
        if (!list.isEmpty()) {
            redisTemplate.opsForValue().set(ALL_CONTROL_DEVICES_KEY, list, 24, TimeUnit.HOURS);
        }
        log.info("Redis 全量控制设备缓存已刷新，当前设备总数: {}", list.size());
    }

    // todo 后续可以实现某个控制设备开启自动,而不是开启自动模式后,所有设备都开启
    private void refreshAutoDeviceCache() {
        List<SysControlDevice> list = this.list(new LambdaQueryWrapper<SysControlDevice>()
                .eq(SysControlDevice::getControlMode, 1));

        redisTemplate.delete(AUTO_DEVICE_KEY);

        if (!list.isEmpty()) {
            redisTemplate.opsForValue().set(AUTO_DEVICE_KEY, list, 24, TimeUnit.HOURS);
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
                String key = "iot:device:active:" + device.getDeviceCode();
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



    @Override
    public void controlDevice(Long deviceId, Integer status) {
        SysControlDevice device = this.getById(deviceId);
        if (device == null) return;

        log.info("执行设备控制: 设备名称={}, 目标状态={}", device.getDeviceName(), status == 1 ? "开启" : "关闭");

        Map<String, Object> command = new HashMap<>();
        command.put("deviceCode", device.getDeviceCode());
        command.put("command", status == 1 ? "ON" : "OFF");
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
        refreshAllDeviceCache();

        Map<String, Object> deviceUpdateMsg = new HashMap<>();
        deviceUpdateMsg.put("type", "DEVICE_UPDATE");
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
    public void checkAndAutoControl(IotSensorData data) {
        List<SysControlDevice> autoDeviceCache = (List<SysControlDevice>) redisTemplate.opsForValue().get(AUTO_DEVICE_KEY);
        if (autoDeviceCache == null || autoDeviceCache.isEmpty()) {
            return;
        }

        for (SysControlDevice device : autoDeviceCache) {
            BigDecimal currentValue = null;
            // 关联环境阈值的ID
            Long thresholdType = device.getEnvThresholdId();
            
            // 根据设备关联的阈值类型，获取当前传感器数据中对应的数值
            if (Integer.valueOf(1).equals(thresholdType)) currentValue = data.getAirTemp();
            else if (Integer.valueOf(2).equals(thresholdType)) currentValue = data.getAirHumidity();
            else if (Integer.valueOf(3).equals(thresholdType)) currentValue = data.getSoilTemp();
            else if (Integer.valueOf(4).equals(thresholdType)) currentValue = data.getSoilHumidity();
            else if (Integer.valueOf(5).equals(thresholdType)) currentValue = data.getCo2Concentration();
            else if (Integer.valueOf(6).equals(thresholdType)) currentValue = data.getLightIntensity();
            
            if (currentValue == null) continue;

            // 获取关联的阈值配置
            SysEnvThreshold threshold = sysEnvThresholdService.getById(device.getEnvThresholdId());
            if (threshold == null) {
                log.warn("设备 {} 未关联有效的阈值配置", device.getDeviceName());
                continue;
            }

            BigDecimal min = threshold.getMinValue();
            BigDecimal max = threshold.getMaxValue();
            if (min == null || max == null) continue;

            boolean shouldOpen = false;
            boolean shouldClose = false;

            // 根据设备类型判断控制逻辑
            // deviceType: 0 (低开高关, 如加热器/灌溉), 1 (高开低关, 如风扇/降温)
            if (Integer.valueOf(1).equals(device.getDeviceType())) {
                 if (currentValue.compareTo(max) > 0) shouldOpen = true;
                 else if (currentValue.compareTo(min) < 0) shouldClose = true;
            } else {
                 if (currentValue.compareTo(min) < 0) shouldOpen = true;
                 else if (currentValue.compareTo(max) > 0) shouldClose = true;
            }

            if (shouldOpen && (device.getStatus() == null || device.getStatus() == 0)) {
                log.info("【自动控制触发】指标: {}, 当前值: {}, 开启设备: {}", 
                        thresholdType, currentValue, device.getDeviceName());
                controlDevice(device.getId(), 1);
            } else if (shouldClose && (device.getStatus() != null && device.getStatus() == 1)) {
                log.info("【自动控制触发】指标: {}, 当前值: {}, 关闭设备: {}", 
                        thresholdType, currentValue, device.getDeviceName());
                controlDevice(device.getId(), 0);
            }
        }
    }
}
