package yuan.xu.intelligence_agriculture.key;

/**
 * @author cing
 */
public class RedisKey {

    /**
     * 价格区域自增编码
     */
    public static final String GOODS_PRICE_AREA_NO_KEY_PREFIX = "pos:goods:price:area:no:";

    public static final String GOODS_RT_CLASS_PUSH_NO_KEY_PREFIX = "pos:goods:rt:class:push:no";

    /**
     * 精品中药商品编码关系key
     */
    private static final String HIGH_QUALITY_CHINESE_MEDICINES = "pos:goods:high_quality_chinese_medicines_mapping:{goodsNo}";

    public static final String PICTURE_BELOW_PUSH_NO_KEY_PREFIX = "pos:goods:picture:below:push:no";


    public static String getHighQualityTcmKey(String goodsNo) {
        return HIGH_QUALITY_CHINESE_MEDICINES.replace("{goodsNo}", goodsNo);
    }

}
