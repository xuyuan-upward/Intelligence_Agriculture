package yuan.xu.intelligence_agriculture.ext;


import com.dsl.pos.goods.req.reqPrice.QueryInnerReqPriceReq;
import com.dsl.pos.goods.resp.reqPrice.FscResult;
import com.dsl.pos.goods.resp.reqPrice.QueryRequestPriceResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @description: 第三方接口：esb平台接口
 * @author: lcx
 * @date: 2024-02-21 11:42
 */
@FeignClient(name = "sl-goods-esb",  url ="${thirdUrl.mom.esbUrl}")
public interface EsbRemoteService {
    /**
     * 查询内加盟请购价格
     *
     * @param req
     * @return
     */
    @PostMapping("join/item/queryPrice")
    FscResult<QueryRequestPriceResp> queryPrice(@RequestBody QueryInnerReqPriceReq req);


}
