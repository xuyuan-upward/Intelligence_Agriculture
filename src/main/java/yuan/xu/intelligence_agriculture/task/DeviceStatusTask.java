package yuan.xu.intelligence_agriculture.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import yuan.xu.intelligence_agriculture.model.SysGreenhouse;
import yuan.xu.intelligence_agriculture.resp.DeviceStatusResp;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
import yuan.xu.intelligence_agriculture.service.SysSensorDeviceService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static yuan.xu.intelligence_agriculture.key.RedisKey.ALL_ENV_HOUSE;
import static yuan.xu.intelligence_agriculture.websocket.WebSocketServer.WebSocketSendInfo;

/**
 * 设备状态检查定时任务
 * 每 2 秒运行一次，检查 Redis 中的活跃标识，判定设备是否在线
 */
@Component
@Slf4j
public class DeviceStatusTask {

    @Autowired
    private SysControlDeviceService sysControlDeviceService;

    @Autowired
    private SysSensorDeviceService sysSensorDeviceService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 上一次的状态快照，用于对比是否发生变化, key:环境envCode
     */
    private Map<String, Map<String, Integer>> lastAllControlStatusMap = new HashMap<>();
    /**
     * 上一次的采集设备状态快照，用于对比是否发生变化,key:环境envCode
     */
    private Map<String,Map<String, Integer>> lastAllSensorStatusMap = new HashMap<>();

    /**
     * 每 2 秒执行一次状态检查
     * 检查采集和控制设备的在线状态，发生变更推送给前端
     */
    @Scheduled(fixedRate = 2000)
    public void checkDeviceStatus() {
        // 推送采集设备在线状态给前端
        /// 1.获取环境实例
        List<SysGreenhouse> sysGreenhouseList = (List<SysGreenhouse>) redisTemplate.opsForValue().get(ALL_ENV_HOUSE);
        if (sysGreenhouseList == null) return;
        
        for (SysGreenhouse sysGreenhouse : sysGreenhouseList) {
            String envCode = sysGreenhouse.getEnvCode();
            
            ///  2.判断对应采集设备状态是否离线
            Map<String, Integer> sensorStatusMap = sysSensorDeviceService.listAllDevicesStatus(envCode);
            if (sensorStatusMap == null) sensorStatusMap = new HashMap<>();
            
            Map<String, Integer> lastSensorMap = lastAllSensorStatusMap.get(envCode);
            if (!sensorStatusMap.equals(lastSensorMap)) {
                lastAllSensorStatusMap.put(envCode, new HashMap<>(sensorStatusMap));
                List<DeviceStatusResp> dtoList = sensorStatusMap.entrySet().stream()
                        .map(e -> new DeviceStatusResp(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());
                WebSocketSendInfo("SENSOR_DEVICE_STATUS", envCode, dtoList);
            }
        }
    }

}
