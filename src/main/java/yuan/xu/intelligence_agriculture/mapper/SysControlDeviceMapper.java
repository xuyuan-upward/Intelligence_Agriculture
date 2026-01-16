package yuan.xu.intelligence_agriculture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;
import yuan.xu.intelligence_agriculture.model.SysControlDevice;
import yuan.xu.intelligence_agriculture.req.EnvThresholdReq;

import java.util.List;

@Mapper
public interface SysControlDeviceMapper extends BaseMapper<SysControlDevice> {

}
