package yuan.xu.intelligence_agriculture.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 控制设备实体类
 * 对应数据库表 sys_control_device
 */
@Data
@TableName("sys_control_device")
public class SysControlDevice implements Serializable {
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

    /**
     * 关联环境阈值ID
     */
    private Long envThresholdId;

    /**
     * 控制模式 (0:手动, 1:自动)
     */
    private Integer controlMode;

    /**
     * 当前开关状态 (0:关, 1:开)
     */
    private Integer status;

    /**
     * 在线状态 (0: 离线, 1: 在线) - 非数据库字段
     */
    @TableField(exist = false)
    private Integer onlineStatus;

    private Date updateTime;
    private Date createTime;
}
