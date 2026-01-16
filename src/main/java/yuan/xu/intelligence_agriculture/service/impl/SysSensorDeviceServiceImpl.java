package yuan.xu.intelligence_agriculture.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.mapper.SysSensorDeviceMapper;
import yuan.xu.intelligence_agriculture.model.SysSensorDevice;
import yuan.xu.intelligence_agriculture.service.SysSensorDeviceService;

import java.util.HashMap;
import java.util.Map;

import static yuan.xu.intelligence_agriculture.key.RedisKey.DEVICE_LAST_ACTIVE_KEY;


/**
 * 采集设备管理业务实现类
 */
@Service
@Slf4j
public class SysSensorDeviceServiceImpl extends ServiceImpl<SysSensorDeviceMapper, SysSensorDevice> implements SysSensorDeviceService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

//
//    @PostConstruct
//    public void init() {
//        refreshAllDeviceCache();
//
//    }
//

    /**
     * 判断对应采集设备状态是否离线,并获取判断后的所有设备状态
     * @return
     */
    @Override
    public Map<String, Integer> listAllDevicesStatus(String greenHouseCode) {
        Map<String,  Map<Object, Object> > map = new HashMap<>();
        // todo 后续加envCode
        Map<Object, Object> lastActiveMap = redisTemplate.opsForHash().entries(DEVICE_LAST_ACTIVE_KEY + greenHouseCode);
        Map<String, Integer> statusMap = new HashMap<>();
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
