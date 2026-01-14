package yuan.xu.intelligence_agriculture.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.mapper.SysEnvThresholdMapper;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.service.SysEnvThresholdService;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SysEnvThresholdServiceImpl extends ServiceImpl<SysEnvThresholdMapper, SysEnvThreshold> implements SysEnvThresholdService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    private static final String ALL_ENV_THRESHOLD_KEY = "iot:all_env_thresholds";

    @PostConstruct
    public void init() {

        refreshEnvThresholdCache();
    }

    // 环境阈值预缓存
    private void refreshEnvThresholdCache() {
        List<SysEnvThreshold> list = list();
        redisTemplate.delete(ALL_ENV_THRESHOLD_KEY);
        if (!list.isEmpty()) {
            redisTemplate.opsForValue().set(ALL_ENV_THRESHOLD_KEY, list, 24, TimeUnit.HOURS);
        }
        log.info("Redis 环境阈值缓存已刷新，当前环境阈值数量: {}", list.size());
    }
}
