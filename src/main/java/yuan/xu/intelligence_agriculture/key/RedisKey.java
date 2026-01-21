package yuan.xu.intelligence_agriculture.key;

/**
 * @author cing
 */
public class RedisKey {

    /**
     * Redis 中存储 ”环境实例对象“ 的 Key 前缀
     */
    public static final String ALL_ENV_HOUSE = "iot:all_env_house:";
    /**
     * Redis 中存储某个环境实例下的所有 “采集设备” 最后在线时间的 Key 前缀
     */
    public  static final String DEVICE_LAST_TIME_KEY = "iot:device:last:time:";

    /**
     * Redis 中存储存储某个环境实例下的所有 “环境参数类型” 的 Key 前缀
     */
    public static final String ENV_THRESHOLD_KEY = "iot:env_thresholds:";

    /**
     * Redis 中存储某个环境实例下的所有 ”控制设备” Key 前缀
     */
    public static final String AUTO_DEVICE_KEY = "iot:auto_devices:";
    /**
     * Redis 中存储某个环境实例下的每个 ”控制设备” 的状态 Key 前缀，初始化状态默认 0 关闭
     */
    public static final String AUTO_DEVICE_STATUS_KEY = "iot:auto_devices:status:";

    /**
     * Redis 中存储某个环境实例下的 “控制模式” Key 前缀
     */
    public static final String AUTO_MODE_KEY = "iot:auto_mode:";
    /**
     * Redis 中存储某个环境实例下的每个 ”控制设备” 最近一次"开灯"时间的 Key 前缀
     */
    ///  1.记录的是当前设备最近一次"开灯"时间
    public static final String AUTO_DEVICE_LIGHT_ON_UNTIL_KEY = "iot:auto_devices:light_on_until:";

    ///  2.记录的是当前设备最近一次"光照强度低于最小值"的时间
    public static final String AUTO_DEVICE_LIGHT_LOW_SINCE_KEY = "iot:auto_devices:light_low_since:";

    public static final String AUTH_SMS_COOLDOWN_KEY = "auth:sms:cooldown:";
    public static final String AUTH_SMS_CODE_KEY = "auth:sms:code:";


}
