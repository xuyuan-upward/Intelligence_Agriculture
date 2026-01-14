package yuan.xu.intelligence_agriculture.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 采集设备实体类
 * 对应数据库表 sys_sensor_device
 */
@Data
@TableName("sys_sensor_device")
public class SysSensorDevice implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 设备编号
     */
    private String deviceCode;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 所属环境
     */
    private String greenhouseEnvCode;

//    /**
//     * 设备类型 (11:TEMP, 12:HUM, 13:S_TEMP, 14:S_HUM, 15:LIGHT_S, 16:CO2_S)
//     */
//    private Integer deviceType;

    /**
     * 在线状态 (0: 离线, 1: 在线) - 非数据库字段
     */
    @TableField(exist = false)
    private Integer onlineStatus;

    private Date updateTime;
    private Date createTime;
}
