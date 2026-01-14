package yuan.xu.intelligence_agriculture.controller;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.model.IotSensorData;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.model.SysSensorDevice;
import yuan.xu.intelligence_agriculture.req.DeviceControlReq;
import yuan.xu.intelligence_agriculture.req.DeviceModeReq;
import yuan.xu.intelligence_agriculture.req.IotSensorDataResp;
import yuan.xu.intelligence_agriculture.resp.DeviceStatusResp;
import yuan.xu.intelligence_agriculture.service.AiPredictionService;
import yuan.xu.intelligence_agriculture.service.IotDataService;
import yuan.xu.intelligence_agriculture.service.SysControlDeviceService;
import yuan.xu.intelligence_agriculture.service.SysSensorDeviceService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能农业对外 API 控制器
 * 提供传感器数据查询、设备控制、状态监控及 AI 预测等接口
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {

    @Autowired
    private IotDataService iotDataService;

    @Autowired
    private SysControlDeviceService sysControlDeviceService;

    @Autowired
    private SysSensorDeviceService sysSensorDeviceService;

    @Autowired
    private AiPredictionService aiPredictionService;
    /**
     * 获取所有控制设备的实时在线状态
     * 数据来源：实时计算 (优先从缓存获取设备列表)
     * @return Map<DeviceCode, Status(0:离线, 1:在线)>
     */
    @GetMapping("/device/status")
    public CommonResult<List<DeviceStatusResp>> getAllDeviceStatus() {
        List<SysControlDevice> devices = sysControlDeviceService.listAllDevicesFromCache();
        List<DeviceStatusResp> deviceStatusResps = new ArrayList<>();
        for (SysControlDevice device : devices) {
            DeviceStatusResp deviceStatusResp = new DeviceStatusResp();
            deviceStatusResp.setDeviceCode(device.getDeviceCode());
            deviceStatusResp.setStatus(device.getStatus());
            deviceStatusResps.add(deviceStatusResp);
        }
        return CommonResult.success(deviceStatusResps);
    }

    /**
     * 获取最新的传感器环境数据
     * 用于首页大屏或实时看板展示
     */
    @GetMapping("/data/current")
    public CommonResult<IotSensorDataResp> getCurrentData() {
        IotSensorData data = iotDataService.lambdaQuery()
                .orderByDesc(IotSensorData::getCreateTime)
                .last("LIMIT 1")
                .one();
        IotSensorDataResp iotSensorDataResp = new IotSensorDataResp();
        BeanUtils.copyProperties(data,iotSensorDataResp );
        return CommonResult.success(iotSensorDataResp);
    }

    /**
     * 获取历史传感器数据列表
     * 默认返回最近的 100 条记录，用于趋势图表展示
     */
    @GetMapping("/data/history")
    public CommonResult<List<IotSensorDataResp>> getHistoryData() {
        List<IotSensorData> list = iotDataService.lambdaQuery()
                .orderByDesc(IotSensorData::getCreateTime)
                .last("LIMIT 100")
                .list();
        List<IotSensorDataResp> iotSensorDataRespList = new ArrayList<>();
        BeanUtils.copyProperties(list,iotSensorDataRespList );
        return CommonResult.success(iotSensorDataRespList);
    }

    /**
     * 获取所有控制设备的基本信息列表
     * 包含设备名称、类型、当前工作状态、控制模式、阈值设置以及实时在线状态
     */
    @GetMapping("/device/control/list")
    public CommonResult<List<SysControlDevice>> getControlDeviceList() {
        return CommonResult.success(sysControlDeviceService.listAllDevicesFromCache());
    }

    /**
     * 获取所有采集设备的基本信息列表
     */
    @GetMapping("/device/sensor/list")
    public CommonResult<List<SysSensorDevice>> getSensorDeviceList() {
        return CommonResult.success(sysSensorDeviceService.listAllDevicesFromCache());
    }

    /**
     * 手动控制设备开关
     * @param req 设备控制请求对象
     */
    @PostMapping("/device/control")
    public CommonResult<String> controlDevice(@RequestBody DeviceControlReq req) {
        sysControlDeviceService.controlDevice(req.getDeviceId(), req.getStatus());
        return CommonResult.success("操作成功");
    }

    /**
     * 更新设备的控制模式及自动控制阈值
     * @param req 设备模式更新请求对象
     */
    @PostMapping("/device/mode")
    public CommonResult<String> updateDeviceMode(@RequestBody DeviceModeReq req) {
        sysControlDeviceService.updateMode(req.getDeviceId(), req.getMode(), req.getMin(), req.getMax());
        return CommonResult.success("Mode updated");
    }

    /**
     * 获取 AI 农作物生长建议或产量预测
     * 调用内部 AI 服务根据历史环境数据生成建议
     */
    @GetMapping("/prediction")
    public CommonResult<String> getPrediction() {
        return CommonResult.success(aiPredictionService.getPrediction());
    }
}
