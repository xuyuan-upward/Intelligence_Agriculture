package yuan.xu.intelligence_agriculture.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 温室/环境实体类
 */
@Data
@TableName("sys_greenhouse")
public class SysGreenhouse implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 环境编码(温室/区域)
     */
    private String envCode;

    /**
     * 环境名称
     */
    private String envName;

    /**
     * 位置描述
     */
    private String location;

    /**
     * 状态(1:启用, 0:停用)
     */
    private Integer status;

    private Date updateTime;
    private Date createTime;
}
