package yuan.xu.intelligence_agriculture.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dsl.cdp.mybatis.plus.GeneralModel;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 中药陈列管理
 *
 * @author dsl-generator
 * @date 2023-06-02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chinese_medicine_display")
public class ChineseMedicineDisplay extends GeneralModel<ChineseMedicineDisplay> {

    private static final long serialVersionUID = 1L;

    /**
     * 租户ID，包含加盟，联盟，大参林等
     */
    private Long tenantId;


    /**
     * 门店id
     */
    private Long departmentId;


    /**
     * 商品id
     */
    private Long goodsId;


    /**
     * 斗柜列编码/货架编码
     */
    private String columnNo;


    /**
     * 斗柜行编码/货架层编码
     */
    private String lineNo;


    /**
     * 斗内编码/位置列数
     */
    private String bucketNo;

    /**
     * 自定位置
     */
    private String customPosition;

    /**
     * 备注
     */
    private String remark;


}
