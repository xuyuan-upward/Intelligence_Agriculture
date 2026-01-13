package yuan.xu.intelligence_agriculture.util;

import cn.hutool.core.util.StrUtil;
import com.dsl.core.config.BusinessException;
import com.dsl.core.config.IRes;
import com.dsl.pos.goods.enums.ResultCode;

/**
 * 类似断言异常判断，及发布手动抛出业务异常
 */
public class Assert {
    /**
     * 如果对象空抛异常
     * @param object
     * @param msg
     */
    public static void notNull(Object object, String msg) {
        if (object == null) {
            throw new BusinessException(ResultCode.BUSINESS_EXECUTION_FAILED.code(),msg);
        }
    }
    /**
     * 如果对象空和长度等于0,不包含空字符串 如" "
     *         System.out.println(StringUtils.isEmpty(""));//ture
     *         System.out.println(StringUtils.isEmpty(" "));//false
     *         System.out.println(StringUtils.isEmpty("  "));//false
     *         System.out.println(StringUtils.isEmpty(null));//true
     *         System.out.println(StringUtils.isEmpty("null"));//false
     *         System.out.println(StringUtils.isEmpty("  empty  "));//false

     * @param s
     * @param msg
     */
    public static void notEmpty(CharSequence s, String msg) {
        if (StrUtil.isEmpty(s)) {
            throw new BusinessException(ResultCode.BUSINESS_EXECUTION_FAILED.code(),msg);
        }
    }
    /**
     * 如果对象空抛异常包含空字符串如：" "
     *
     *  System.out.println(StringUtils.isBlank(""));//ture
     *         System.out.println(StringUtils.isBlank(" "));//true
     *         System.out.println(StringUtils.isBlank("  "));//true
     *         System.out.println(StringUtils.isBlank(null));//true
     *         System.out.println(StringUtils.isBlank("null"));//false
     *         System.out.println(StringUtils.isBlank("  blank  "));//false
     *         //制表符
     *         System.out.println(StringUtils.isBlank("\t"));//true
     * @param s
     * @param msg
     */
    public static void notBlank(CharSequence s, String msg) {
        if (StrUtil.isBlank(s)) {
            throw new BusinessException(ResultCode.BUSINESS_EXECUTION_FAILED.code(),msg);
        }
    }

    /**
     * 不等于空抛异常
     * @param s
     * @param msg
     */
    public static void Blank(CharSequence s, String msg) {
        if (!StrUtil.isBlank(s)) {
            throw new BusinessException(ResultCode.BUSINESS_EXECUTION_FAILED.code(),msg);
        }
    }

    /**
     * 抛出异常
     * @param msg
     */
    public static void fail(String msg) {
        throw new BusinessException(ResultCode.BUSINESS_EXECUTION_FAILED.code(),msg);
    }
    /**
     * 抛出异常类
     * @param errorCode
     */
    public static void fail(IRes errorCode) {
        throw new BusinessException(errorCode.getCode(),errorCode.getMessage());
    }

    /**
     * true时抛出异常
     * @param expression
     * @param message
     */
    public static void isTrue(boolean expression, String message) {
        if (expression) {
            throw new BusinessException(ResultCode.BUSINESS_EXECUTION_FAILED.code(),message);
        }
    }
}
