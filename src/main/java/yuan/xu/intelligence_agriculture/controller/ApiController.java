package yuan.xu.intelligence_agriculture.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysDevice;
import yuan.xu.intelligence_agriculture.service.AiPredictionService;
import yuan.xu.intelligence_agriculture.service.IotDataService;
import yuan.xu.intelligence_agriculture.service.SysDeviceService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {

    @Autowired
    private IotDataService iotDataService;

    @Autowired
    private SysDeviceService sysDeviceService;

    @Autowired
    private AiPredictionService aiPredictionService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取所有设备的在线状态 (从 Redis 读取)
     * @return Map<DeviceCode, Status(0:离线, 1:在线)>
     */
    @GetMapping("/device/status")
    public CommonResult<Map<Object, Object>> getAllDeviceStatus() {
        Map<Object, Object> statusMap = redisTemplate.opsForHash().entries("iot:device:online_status");
        return CommonResult.success(statusMap);
    }

    @GetMapping("/data/current")
    public CommonResult<IotSensorData> getCurrentData() {
        IotSensorData data = iotDataService.lambdaQuery()
                .orderByDesc(IotSensorData::getCreateTime)
                .last("LIMIT 1")
                .one();
        return CommonResult.success(data);
    }

    @GetMapping("/data/history")
    public CommonResult<List<IotSensorData>> getHistoryData() {
        List<IotSensorData> list = iotDataService.lambdaQuery()
                .orderByDesc(IotSensorData::getCreateTime)
                .last("LIMIT 100")
                .list();
        return CommonResult.success(list);
    }

    @GetMapping("/device/list")
    public CommonResult<List<SysDevice>> getDeviceList() {
        return CommonResult.success(sysDeviceService.list());
    }

    @PostMapping("/device/control")
    public CommonResult<String> controlDevice(@RequestBody Map<String, Object> params) {
        Long deviceId = Long.valueOf(params.get("deviceId").toString());
        Integer status = Integer.valueOf(params.get("status").toString());
        sysDeviceService.controlDevice(deviceId, status);
        return CommonResult.success("Operation successful");
    }

    @PostMapping("/device/mode")
    public CommonResult<String> updateDeviceMode(@RequestBody Map<String, Object> params) {
        Long deviceId = Long.valueOf(params.get("deviceId").toString());
        Integer mode = Integer.valueOf(params.get("mode").toString());
        BigDecimal min = params.get("min") != null ? new BigDecimal(params.get("min").toString()) : null;
        BigDecimal max = params.get("max") != null ? new BigDecimal(params.get("max").toString()) : null;
        
        sysDeviceService.updateMode(deviceId, mode, min, max);
        return CommonResult.success("Mode updated");
    }

    @GetMapping("/prediction")
    public CommonResult<String> getPrediction() {
        return CommonResult.success(aiPredictionService.getPrediction());
    }
}
