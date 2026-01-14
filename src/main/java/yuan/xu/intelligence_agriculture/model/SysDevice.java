package yuan.xu.intelligence_agriculture.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 农业设备控制实体类
 * 对应数据库表 sys_device，记录设备基础信息、当前状态及自动控制阈值
 */
@Data
@TableName("sys_device")
public class SysDevice implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 设备唯一编码（用于 MQTT 通信识别）
     */
    private String deviceCode;

    /**
     * 设备名称（如：1号大棚排风扇）
     */
    private String deviceName;

    /**
     * 设备类型 (1:风扇, 2:水泵, 3:补光灯, 4:加热器, 5:加湿器, 6:CO2发生器, 
     *          11:空气温度计, 12:空气湿度计, 13:土壤温度计, 14:土壤湿度计, 15:光照计, 16:CO2检测仪)
     */
    private Integer deviceType;

    /**
     * 设备当前状态 (0: 关闭, 1: 开启)
     */
    private Integer status;

    /**
     * 控制模式 (0: 手动模式, 1: 自动模式)
     */
    private Integer controlMode;

    /**
     * 自动控制关联的传感器类型 (1:空气温度, 2:空气湿度, 3:土壤温度, 4:土壤湿度, 5:光照强度, 6:CO2浓度)
     */
    private Integer autoThresholdType;

    /**
     * 自动控制最小阈值
     */
    private BigDecimal thresholdMin;

    /**
     * 自动控制最大阈值
     */
    private BigDecimal thresholdMax;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建时间
     */
    private Date createTime;
}
