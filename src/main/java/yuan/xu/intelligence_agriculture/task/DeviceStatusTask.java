package yuan.xu.intelligence_agriculture.task;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import yuan.xu.intelligence_agriculture.model.SysDevice;
import yuan.xu.intelligence_agriculture.service.SysDeviceService;
import yuan.xu.intelligence_agriculture.websocket.WebSocketServer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备状态检查定时任务
 * 每 2 秒运行一次，检查 Redis 中的活跃标识，判定设备是否在线
 */
@Component
@Slf4j
public class DeviceStatusTask {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SysDeviceService sysDeviceService;

    private static final String DEVICE_LAST_ACTIVE_KEY = "iot:device:active:";
    private static final String DEVICE_ONLINE_STATUS_KEY = "iot:device:online_status";

    /**
     * 上一次的状态快照，用于对比是否发生变化
     */
    private Map<String, Integer> lastStatusMap = new HashMap<>();

    /**
     * 每 2 秒执行一次状态检查
     */
    @Scheduled(fixedRate = 2000)
    public void checkDeviceStatus() {
        // 1. 获取所有设备列表
        List<SysDevice> devices = sysDeviceService.list();
        Map<String, Integer> currentStatusMap = new HashMap<>();

        long currentTime = System.currentTimeMillis();

        for (SysDevice device : devices) {
            String deviceCode = device.getDeviceCode();
            // 2. 从 Redis 获取最后活跃时间
            Object lastActiveObj = redisTemplate.opsForValue().get(DEVICE_LAST_ACTIVE_KEY + deviceCode);
            
            int isOnline = 0; // 默认离线
            if (lastActiveObj != null) {
                long lastActiveTime = Long.parseLong(lastActiveObj.toString());
                // 3. 判断逻辑：如果当前时间与最后活跃时间间隔小于 6000ms (6秒)，则认为在线
                if (currentTime - lastActiveTime < 6000) {
                    isOnline = 1;
                }
            }
            
            currentStatusMap.put(deviceCode, isOnline);
        }

        // 4. 将汇总后的状态存入 Redis Hash 中
        redisTemplate.opsForHash().putAll(DEVICE_ONLINE_STATUS_KEY, currentStatusMap);
        
        // 5. 如果状态发生了变化，或者这是第一次运行，则通过 WebSocket 推送给前端
        if (!currentStatusMap.equals(lastStatusMap)) {
            log.info("设备在线状态发生变化，触发 WebSocket 推送: {}", currentStatusMap);
            
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "DEVICE_STATUS");
            wsMessage.put("data", currentStatusMap);
            
            WebSocketServer.sendInfo(JSONUtil.toJsonStr(wsMessage));
            
            // 更新快照
            lastStatusMap = new HashMap<>(currentStatusMap);
        }
        
        log.debug("设备在线状态检查完成");
    }
}
