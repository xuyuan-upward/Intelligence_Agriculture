package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.resp.DeviceStatusResp;
import yuan.xu.intelligence_agriculture.dto.SensorData;
import yuan.xu.intelligence_agriculture.resp.SystemLogResp;
import yuan.xu.intelligence_agriculture.mapper.SysControlDeviceMapper;
import yuan.xu.intelligence_agriculture.mapper.SysControlLogMapper;
import yuan.xu.intelligence_agriculture.mapper.SysEnvThresholdMapper;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.model.SysControlLog;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.mqtt.MqttGateway;
import yuan.xu.intelligence_agriculture.req.DeviceModeReq;
import yuan.xu.intelligence_agriculture.req.DeviceModeReqs;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
import yuan.xu.intelligence_agriculture.service.SysEnvThresholdService;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static yuan.xu.intelligence_agriculture.enums.ControlStatus.OFF;
import static yuan.xu.intelligence_agriculture.enums.ControlStatus.ON;
import static yuan.xu.intelligence_agriculture.enums.EnvParameterType.*;
import static yuan.xu.intelligence_agriculture.key.EnvironmentKey.CONTROL;
import static yuan.xu.intelligence_agriculture.key.RedisKey.*;
import static yuan.xu.intelligence_agriculture.websocket.WebSocketServer.WebSocketSendInfo;

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
    @Autowired
    private SysEnvThresholdMapper sysEnvThresholdMapper;


    @PostConstruct
    public void init() {
        // 自动控制设备缓存
//        refreshAutoDeviceCache();
//        refreshAllDeviceCache();
    }

//    private void refreshAllDeviceCache() {
//        List<SysControlDevice> list = this.list();
//        redisTemplate.delete(ALL_CONTROL_DEVICES_KEY);
//        if (!list.isEmpty()) {
//            redisTemplate.opsForList().rightPushAll(ALL_CONTROL_DEVICES_KEY, list);
//            // 2. 为整个列表设置过期时间（例如：1小时）
//            redisTemplate.expire(ALL_CONTROL_DEVICES_KEY, 24, TimeUnit.HOURS);
//        }
//        log.info("Redis 全量控制设备缓存已刷新，当前设备总数: {}", list.size());
//    }

/*    // todo 后续可以实现某个控制设备开启自动,而不是开启自动模式后,所有设备都开启
    private void refreshAutoDeviceCache(String envCode) {
        // 获取某个环境下的自动控制模式下的所有设备
        List<SysControlDevice> list = this.list(new LambdaQueryWrapper<SysControlDevice>()
                .eq(SysControlDevice::getControlMode, 1).eq(SysControlDevice::getGreenhouseEnvCode, envCode));

        redisTemplate.delete(AUTO_DEVICE_KEY + envCode);
        if (!list.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(AUTO_DEVICE_KEY + envCode, list);
            // 2. 为整个列表设置过期时间（例如：1小时）
            redisTemplate.expire(AUTO_DEVICE_KEY + envCode, 24, TimeUnit.HOURS);
        }
        log.info("Redis 自动控制设备缓存已刷新，当前自动控制模式环境编码: {},设备数量: {}", envCode, list.size());
    }*/

    // todo 后续可以实现某个控制设备开启自动,而不是开启自动模式后,所有设备都开启
    private void refreshSingleAutoDeviceCache(String envCode, String deviceCode) {
        // 获取某个环境下的自动控制模式下的所有设备
        SysControlDevice sysControlDevice = this.baseMapper.selectOne(new LambdaQueryWrapper<SysControlDevice>()
                .eq(SysControlDevice::getControlMode, 1)
                .eq(SysControlDevice::getGreenhouseEnvCode, envCode)
                .eq(SysControlDevice::getDeviceCode, deviceCode));

        redisTemplate.delete(AUTO_DEVICE_KEY + envCode);

        if (sysControlDevice != null) {
            redisTemplate.opsForList().rightPushAll(AUTO_DEVICE_KEY + envCode + deviceCode, sysControlDevice);
            // 2. 为整个列表设置过期时间（例如：1小时）
            redisTemplate.expire(AUTO_DEVICE_KEY + envCode, 24, TimeUnit.HOURS);
        }
        log.info("Redis 自动控制设备缓存已刷新，当前自动控制模式环境编码: {},设备编码: {}", envCode, sysControlDevice.getDeviceCode());
    }


    /**
     * 下发命令&修改控制设备状态
     *
     * @param deviceCode 对应的设备唯一编码
     * @param status
     * @param envCode
     */
    @Override
    public void controlDevice(String deviceCode, Integer status, String envCode) {
        LambdaQueryWrapper<SysControlDevice> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysControlDevice::getDeviceCode, deviceCode);
        SysControlDevice device = this.getOne(queryWrapper);
        if (device == null) {
            log.warn("未找到设备: {}", deviceCode);
            return;
        }
        if (deviceCode == null || envCode == null || status == null) return;
        Map<String, Object> command = new HashMap<>();
        command.put("envCode", envCode);
        command.put("deviceCode", deviceCode);
        command.put("command", status);
        // 发送给MQTT
        mqttGateway.sendToMqtt(JSONUtil.toJsonStr(command), CONTROL + envCode + "/" + deviceCode);


        SysControlLog logEntry = new SysControlLog();
        logEntry.setDeviceCode(deviceCode);
        logEntry.setOperationType(device.getControlMode());
        logEntry.setCreateTime(new Date());
        // todo 控制日志看是否要添加
//        sysControlLogMapper.insert(logEntry);

        // 更新数据库中的设备状态
        device.setStatus(status);
        device.setUpdateTime(new Date());
        this.updateById(device);


        // todo 控制日志看后续完善
        SystemLogResp logDTO = new SystemLogResp();
        logDTO.setType(device.getControlMode() == 1 ? "success" : "primary");
        logDTO.setSource(device.getControlMode() == 1 ? "自动控制" : "手动控制");
        logDTO.setMessage(device.getDeviceName() + (status == 1 ? " 已开启" : " 已关闭"));
        WebSocketSendInfo("SYSTEM_LOG", envCode, logDTO);
    }

    /**
     * 更新某个环境的"单个"设备的控制模式
     */
    @Override
    public void updateSingleMode(DeviceModeReq req) {
        Integer mode = req.getMode();
        String deviceCode = req.getDeviceCode();
        String envCode = req.getEnvCode();
        boolean flag = this.update(
                Wrappers.<SysControlDevice>lambdaUpdate()
                        .eq(SysControlDevice::getDeviceCode, deviceCode)
                        .eq(SysControlDevice::getGreenhouseEnvCode, envCode).set(SysControlDevice::getControlMode, mode)
        );
        if (!flag) {
            return;
        }
        // 刷新的是哪个环境下的阈值模式
        refreshSingleAutoDeviceCache(envCode, deviceCode);
        log.info("更新单个设备模式并同步 Redis 完成: 设备={}, 模式={}",
                deviceCode, mode == 1 ? "自动" : "手动");
    }

    /**
     * todo 目前用来整个环境的判断,后面降低自动模式粒度只对某个设备来判断的时候,可以舍弃
     * @param envCode
     * @return
     */
    private boolean envIsAuto(String envCode) {
        String key = AUTO_MODE_KEY + envCode;
        Integer mode = (Integer) redisTemplate.opsForValue().get(key);
        if (mode != null) {
            return mode == 1;
        }
        /// 获取自动控制的设备
        List<SysControlDevice> devices = fromCacheGetControlDevices(envCode);
        if (devices.isEmpty()) {
            return false;
        }
        // 设定当前
        redisTemplate.opsForValue().set(key, 1);
        return true;
    }

    /**
     * 获取是自动模式下的所有控制设备
     * @param envCode
     * @return
     */
    private List<SysControlDevice> fromCacheGetControlDevices(String envCode) {
        String key = AUTO_DEVICE_KEY + envCode;
        @SuppressWarnings("unchecked")
        List<SysControlDevice> cache =
                (List<SysControlDevice>) redisTemplate.opsForValue().get(key);

        if (cache != null && !cache.isEmpty()) {
            return cache;
        }

        List<SysControlDevice> list = this.list(
                Wrappers.<SysControlDevice>lambdaQuery()
                        .eq(SysControlDevice::getGreenhouseEnvCode, envCode)
                        .eq(SysControlDevice::getControlMode, 1) // 自动控制模式的
        );

        redisTemplate.opsForValue().set(key, list);
        redisTemplate.expire(key, 24, TimeUnit.HOURS);
        return list;
    }

    /**
     * 1. 检查控制模式  2.自动模式:触发对应的控制,并且将对应的控制设备状态推送给前端
     * @param data
     * @param integerSensorDataMap
     */
    @Override
    @SuppressWarnings("unchecked")
    public void checkAndAutoControl(IotSensorData data, Map<Integer, SensorData> integerSensorDataMap) {
        String greenhouseEnvCode = data.getGreenhouseEnvCode();
        // 1 是否自动 (决策入口)
        if (!envIsAuto(greenhouseEnvCode)) {
            return;
        }
        // 2 从缓存中拿控制
        List<SysControlDevice> autoDeviceCache = fromCacheGetControlDevices(greenhouseEnvCode);
        /**
         * 3.获取当前环境下的环境阈值
         * 用map来存储对应不同环境下的环境阈值,key根据不同环境来区分,filed:根据环境ID来区分,value:对应的整个环境阈值对象
         * 为什么filed:根据环境ID来区分?因为对应的控制器只和对应的环境阈值挂钩,其中挂钩是靠:环境阈值ID
         */
        Map<Object, Object> map = redisTemplate.opsForHash().entries(ALL_ENV_THRESHOLD_KEY + data.getGreenhouseEnvCode());
        HashMap<Long, SysEnvThreshold> sysEnvThresholdHashMap = new HashMap<>();
        map.forEach((k, v) -> sysEnvThresholdHashMap.put(Long.valueOf((String) k), (SysEnvThreshold) v));
        if (autoDeviceCache == null || autoDeviceCache.isEmpty() || sysEnvThresholdHashMap == null) {
            return;
        }
        // key:控制设备编码 value:状态
        // 更新前的
        Map<String, Integer> BeforeUpdateControlStatusMap = autoDeviceCache.stream().collect(Collectors.toMap(SysControlDevice::getDeviceCode, SysControlDevice::getStatus));
        // 更新后的
        Map<String, Integer> AfterUpdateControlStatusMap = new HashMap<>(BeforeUpdateControlStatusMap);
        for (SysControlDevice sysControlDevice : autoDeviceCache) {
            autoControlOneDevice(sysControlDevice, data, integerSensorDataMap, sysEnvThresholdHashMap, AfterUpdateControlStatusMap);
        }

        // 统一推送设备状态变更
        if (!AfterUpdateControlStatusMap.isEmpty() && !BeforeUpdateControlStatusMap.equals(AfterUpdateControlStatusMap)) {
            // 刷新缓存
            redisTemplate.delete(AUTO_DEVICE_KEY + data.getGreenhouseEnvCode());

            // Convert Map to List of Objects for frontend
            List<DeviceStatusResp> statusList = new ArrayList<>();
            AfterUpdateControlStatusMap.forEach((k, v) -> {
                statusList.add(new DeviceStatusResp(k, v));
            });

            WebSocketSendInfo("CONTROL_DEVICE_STATUS", greenhouseEnvCode, statusList);
            log.info("自动控制触发批量推送: 环境={}, 设备数={}", greenhouseEnvCode, statusList.size());
        }
    }
    /**
     * 更新某个环境"所有"控制设备的控制模式
     */
    @Override
    public void updatesDevicesMode(DeviceModeReqs reqs) {
        Integer mode = reqs.getMode();
        String envCode = reqs.getEnvCode();
        List<SysControlDevice> sysControlDeviceList = this.list(
                Wrappers.<SysControlDevice>lambdaQuery()
                        .eq(SysControlDevice::getGreenhouseEnvCode, envCode)
        );
        if (sysControlDeviceList.isEmpty()) return;
        for (SysControlDevice sysControlDevice : sysControlDeviceList) {
            sysControlDevice.setControlMode(mode);
        }
        this.updateBatchById(sysControlDeviceList);
        // 刷新的是哪个环境下的阈值模式
        fromCacheGetControlDevices(envCode);
        log.info("更新多个设备模式并同步 Redis 完成: 设备数量={}, 模式={}",
                sysControlDeviceList.size(), mode == 1 ? "自动" : "手动");

    }

    /**
     * 判断某个设备是否处于自动控制模式
     */
    private boolean deviceIsAuto(SysControlDevice device) {
        return device.getControlMode() != null && device.getControlMode() == 1;
    }

    /**
     * 单设备自动检查 + 控制
     * @param device
     * @param data
     * @param sensorDataMap
     * @param thresholdMap
     */
    private void autoControlOneDevice(SysControlDevice device,
                                      IotSensorData data,
                                      Map<Integer, SensorData> sensorDataMap,
                                      Map<Long, SysEnvThreshold> thresholdMap,
                                      Map<String, Integer> updatedDevices) {
        // 设备不是自动模式，直接跳过
        boolean deviceIsAuto = deviceIsAuto(device);
        log.info("设备控制模式:{},设备控制名称:{}", deviceIsAuto == true ? "自动" : "手动",device.getDeviceName());
        log.info("");
        if (!deviceIsAuto) {
            return;
        }

        SysEnvThreshold threshold = thresholdMap.get(device.getEnvThresholdId());
        if (threshold == null) return;

        SensorData sensorData = sensorDataMap.get(threshold.getEnvParameterType());
        if (sensorData == null || sensorData.getData() == null) return;

        BigDecimal currentVal = sensorData.getData();
        BigDecimal min = threshold.getMinValue();
        BigDecimal max = threshold.getMaxValue();
        if (min == null || max == null) return;

        Integer typeCode = threshold.getEnvParameterType();

        boolean shouldOpen = false;
        boolean shouldClose = false;

        // 低开高关（加热、补光）
        if (AIR_TEMP.getEnvParameterType().equals(typeCode)
                || SOIL_TEMP.getEnvParameterType().equals(typeCode)
                || LIGHT_INTENSITY.getEnvParameterType().equals(typeCode)|| AIR_HUMIDITY.getEnvParameterType().equals(typeCode)
                || SOIL_HUMIDITY.getEnvParameterType().equals(typeCode)) {
            if (currentVal.compareTo(min) < 0) shouldOpen = true;
            else if (currentVal.compareTo(min) > 0) shouldClose = true;
        }
        // 高开低关（排风、降碳,补水）
        else if (
               CO2_CONCENTRATION.getEnvParameterType().equals(typeCode)) {

            if (currentVal.compareTo(max) > 0) shouldOpen = true;
            else if (currentVal.compareTo(max) < 0) shouldClose = true;
        }
        
        if (shouldOpen && OFF.getCode().equals(device.getStatus())) {
            controlDevice(device.getDeviceCode(), ON.getCode(), device.getGreenhouseEnvCode());
            // 更新当前对象状态并加入列表
            device.setStatus(ON.getCode());
            updatedDevices.put(device.getDeviceCode(), ON.getCode());
            log.info("自动控制模式开启下:开启-设备{}, 当前值: {}",
                    device.getDeviceName(),   currentVal);
        } else if (shouldClose && ON.getCode().equals(device.getStatus())) {
            controlDevice(device.getDeviceCode(), OFF.getCode(), device.getGreenhouseEnvCode());
            // 更新当前对象状态并加入列表
            device.setStatus(OFF.getCode());
            // 把当前发生变更的状态放进去
            updatedDevices.put(device.getDeviceCode(), OFF.getCode());
            log.info("自动控制模式开启下:关闭-设备{}, 当前值: {}",
                    device.getDeviceName(),   currentVal);
        }
    }

    @Override
    public Map<String, Integer> listAllDevicesStatus(String envCode) {
        Map<String, Integer> statusMap = new HashMap<>();
        Map<Object, Object> lastActiveMap = redisTemplate.opsForHash().entries(DEVICE_LAST_ACTIVE_KEY + envCode);

        if (lastActiveMap == null || lastActiveMap.isEmpty()) {
            return statusMap;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<Object, Object> entry : lastActiveMap.entrySet()) {
            String deviceCode = entry.getKey() == null ? null : entry.getKey().toString();
            if (deviceCode == null || deviceCode.trim().isEmpty()) {
                continue;
            }
            long lastActiveMs = 0L;
            Object v = entry.getValue();
            if (v instanceof Number) {
                lastActiveMs = ((Number) v).longValue();
            } else if (v != null) {
                try {
                    lastActiveMs = Long.parseLong(v.toString());
                } catch (Exception ignored) {
                    lastActiveMs = 0L;
                }
            }
            statusMap.put(deviceCode, (lastActiveMs > 0 && now - lastActiveMs < 6000) ? 1 : 0);
        }
        return statusMap;
    }




}
