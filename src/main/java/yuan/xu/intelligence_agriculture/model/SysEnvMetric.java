package yuan.xu.intelligence_agriculture.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 环境参数类型实体类
 */
@Data
@TableName("sys_env_metric")
public class SysEnvMetric implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 环境参数类型
     */
    private Integer envParameterType;

    /**
     * 环境参数名称
     */
    private String envParameterName;

    private Date updateTime;
    private Date createTime;
}
