package yuan.xu.intelligence_agriculture.key;

/**
 * @author cing
 */
public class RedisKey {

    /**
     * Redis 中存储环境参数类型的 Key 前缀
     */
    public static final String ALL_ENV_HOUSE = "iot:all_env_house:";
    /**
     * Redis 中存储采集设备最后在线时间的 Key 前缀
     */
    public  static final String DEVICE_LAST_ACTIVE_KEY = "iot:device:active:";

    /**
     * Redis 中存储环境参数类型的 Key 前缀
     */
    public static final String ALL_ENV_THRESHOLD_KEY = "iot:all_env_thresholds:";

    /**
     * Redis 中存储控制设备 Key 前缀
     */
    public static final String AUTO_DEVICE_KEY = "iot:auto_devices:";

    /**
     * Redis 中存储控制模式 Key 前缀
     */
    public static final String AUTO_MODE_KEY = "iot:auto_mode:";

    /**
     * Redis 中存储设备最后在线时间的 Key 前缀 todo 可能用不到,看后续项目
     */
    public static final String ALL_CONTROL_DEVICES_KEY = "iot:all_control_devices:";

}
