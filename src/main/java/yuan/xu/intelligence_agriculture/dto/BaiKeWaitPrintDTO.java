package yuan.xu.intelligence_agriculture.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @description:
 * @author: caorunlong
 * @date: 2023/8/4
 */
@Data
public class BaiKeWaitPrintDTO {

    /**
     * 10位店号
     */
    private String longStoreNo;

    /**
     * 英克商品id
     */
    private Long goodsId;

    /**
     * 打印数量
     */
    private Integer tagCount;

    /**
     * 输入日期
     */
    private LocalDateTime entryDate;

    /**
     * 商品编码
     */
    private String goodsNo;


}
