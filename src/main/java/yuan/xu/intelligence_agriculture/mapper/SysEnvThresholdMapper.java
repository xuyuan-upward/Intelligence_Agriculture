package yuan.xu.intelligence_agriculture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import yuan.xu.intelligence_agriculture.model.SysEnvThreshold;
import yuan.xu.intelligence_agriculture.req.EnvThresholdReq;

import java.util.List;

@Mapper
public interface SysEnvThresholdMapper extends BaseMapper<SysEnvThreshold> {
    void batchUpdateEnvThreshold(@Param("envCode") String envCode,
                                @Param("list") List<EnvThresholdReq> list);
}
