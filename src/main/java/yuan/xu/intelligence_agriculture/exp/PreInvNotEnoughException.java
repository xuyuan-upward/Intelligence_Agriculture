//package yuan.xu.intelligence_agriculture.exp;
//
//import com.dsl.cdp.common.constant.ResultCodeEnum;
//import com.dsl.cdp.common.exception.BaseException;
//import org.springframework.http.HttpStatus;
//
///**
// * 用于o2o前置判断库存不足，此异常不能在扣减库存逻辑时使用，不然会有问题
// * @description:
// * @author: caorunlong
// * @date: 2024/3/8
// */
//public class PreInvNotEnoughException extends BaseException {
//
//    public PreInvNotEnoughException() {
//        this(ResultCodeEnum.BAD_REQUEST.code());
//    }
//
//    public PreInvNotEnoughException(String code) {
//        super(code, HttpStatus.BAD_REQUEST, ResultCodeEnum.BAD_REQUEST.getDesc());
//    }
//
//    public PreInvNotEnoughException(String code, String reason) {
//        super(code, HttpStatus.BAD_REQUEST, reason);
//    }
//}
