package yuan.xu.intelligence_agriculture.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 设备控制日志实体类
 * 对应数据库表 sys_control_log，记录设备的手动和自动开关操作记录
 */
@Data
@TableName("sys_control_log")
public class SysControlLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID，自增
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联的设备ID
     */
    private String deviceCode;

    /**
     * 操作类型（MANUAL: 手动, AUTO: 自动）
     */
    private Integer operationType;

    /**
     * 操作发生时间
     */
    private Date createTime;
    /**
     * 操作更新
     */
    private Date updateTime;
}
