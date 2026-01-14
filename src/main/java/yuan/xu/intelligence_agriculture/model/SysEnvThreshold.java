package yuan.xu.intelligence_agriculture.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 环境阈值定义实体类
 */
@Data
@TableName("sys_env_threshold")
public class SysEnvThreshold implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属环境编码
     */
    private String greenhouseEnvCode;

    /**
     * 环境参数类型
     */
    private Integer envParameterType;

    /**
     * 阈值下限
     */
    private BigDecimal minValue;

    /**
     * 阈值上限
     */
    private BigDecimal maxValue;

    private Date updateTime;
    private Date createTime;
}
