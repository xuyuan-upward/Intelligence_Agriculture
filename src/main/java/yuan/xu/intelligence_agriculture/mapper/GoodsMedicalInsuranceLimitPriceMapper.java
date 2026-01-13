package yuan.xu.intelligence_agriculture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dsl.core.model.GoodsMedicalInsuranceLimitPrice;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品医保限价DAO接口
 *
 * @author lihaojie
 * @date 2025-07-11
 */
public interface GoodsMedicalInsuranceLimitPriceMapper extends BaseMapper<GoodsMedicalInsuranceLimitPrice> {

    /**
     * 批量查询商品医保限价
     *
     * @param longStoreNo 门店编码
     * @param goodsNoList 商品编码集合
     * @return 商品医保限价列表
     */
    List<GoodsMedicalInsuranceLimitPrice> batchQueryByStoreAndGoods(@Param("longStoreNo") String longStoreNo,
                                                                   @Param("goodsNoList") List<String> goodsNoList);
} 