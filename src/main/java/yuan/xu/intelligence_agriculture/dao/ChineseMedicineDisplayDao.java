//package yuan.xu.intelligence_agriculture.dao;
//
//import com.baomidou.dynamic.datasource.annotation.DS;
//import com.baomidou.mybatisplus.core.metadata.IPage;
//import com.dsl.cdp.mybatis.annotation.AuthorityFilter;
//import com.dsl.cdp.mybatis.plus.GeneralDao;
//import com.dsl.core.constant.DBConstant;
//import com.dsl.core.dto.MedicineWeighDTO;
//import com.dsl.core.dto.StoreMedicineWeighQO;
//import com.dsl.core.model.ChineseMedicineDisplay;
//import com.dsl.pos.goods.req.display.ChineseMedicineDisplayPageReq;
//import com.dsl.pos.goods.resp.display.ChineseMedicineDisplayPageResp;
//import org.apache.ibatis.annotations.Param;
//
//import java.util.List;
//
///**
// * <p>
// * 中药陈列管理 Mapper 接口
// * </p>
// *
// * @author dsl-generator
// * @since 2023-06-02
// */
//public interface ChineseMedicineDisplayDao extends GeneralDao<ChineseMedicineDisplay> {
//
//    /**
//     * 分页查询
//     * @param iPage
//     * @param chineseMedicineDisplayPageReq
//     * @return
//     */
//    @AuthorityFilter
//    List<ChineseMedicineDisplayPageResp> pageQuery(IPage<ChineseMedicineDisplayPageResp> iPage, @Param("req") ChineseMedicineDisplayPageReq chineseMedicineDisplayPageReq);
//
//    /**
//     * 中药斗谱查询
//     * @param storeMedicineWeighQO 门店编码
//     * @return 中药斗谱列表
//     */
//    @DS(DBConstant.GOODS_DS_SLAVE)
//    List<MedicineWeighDTO> storeMedicineWeighList(StoreMedicineWeighQO storeMedicineWeighQO);
//}
