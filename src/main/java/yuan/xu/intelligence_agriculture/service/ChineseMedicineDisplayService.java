package yuan.xu.intelligence_agriculture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dsl.cdp.common.dto.PageDto;
import com.dsl.cdp.core.CloudService;
import com.dsl.core.model.ChineseMedicineDisplay;
import com.dsl.pos.goods.req.display.*;
import com.dsl.pos.goods.resp.display.ChineseMedicineDisplayPageResp;
import com.dsl.pos.goods.resp.display.MedicineWeighVo;

import java.util.List;
import java.util.Map;

/**
* @description ChineseMedicineDisplayService
* @author dsl-generator
* @date 2023-06-02 16:08:41
*/
public interface
ChineseMedicineDisplayService  extends CloudService, IService<ChineseMedicineDisplay> {

    /**
    * 中药陈列管理-分页查询
    *
    * @param chineseMedicineDisplayPageReq chineseMedicineDisplayPageReq
    * @return PageDto<ChineseMedicineDisplayPageResp> 
    */
    PageDto<ChineseMedicineDisplayPageResp> page(ChineseMedicineDisplayPageReq chineseMedicineDisplayPageReq);

    /**
    * 中药陈列管理-新增
    *
    * @param chineseMedicineDisplayInsertReq chineseMedicineDisplayInsertReq
    * 
    */
    void insert(ChineseMedicineDisplayInsertReq chineseMedicineDisplayInsertReq);

    /**
    * 中药陈列管理-更新
    *
    * @param chineseMedicineDisplayUpdateReq chineseMedicineDisplayUpdateReq
    * 
    */
    void update(ChineseMedicineDisplayUpdateReq chineseMedicineDisplayUpdateReq);

    /**
    * 中药陈列管理-批量删除
    *
    * @param ids ids
    * 
    */
    void delete(List<Long> ids);


    /**
     * 中药陈列管理-百科更新中药斗谱
     *
     * @param bkMedicineWeighUpdateReq 中药斗谱信息
     *
     */
    void bkUpdateChineseMedicine(BkMedicineWeighUpdateReq bkMedicineWeighUpdateReq);

    /**
     * 百科中药斗谱查询
     * @param longStoreNo 10位门店编码
     * @return 中药斗谱列表
     */
    List<MedicineWeighVo> storeMedicineWeighList(String longStoreNo);

    /**
     * 小票查询中药斗谱
     * @param ticketChineseMedicineReq 门店商品
     * @return 中药斗谱
     */
    Map<Long, String> queryTicketPosition(TicketChineseMedicineReq ticketChineseMedicineReq);
}