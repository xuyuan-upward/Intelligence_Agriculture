package yuan.xu.intelligence_agriculture.service;

import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.req.LoginReq;
import yuan.xu.intelligence_agriculture.req.RegisterReq;
import yuan.xu.intelligence_agriculture.req.SendCodeReq;
import yuan.xu.intelligence_agriculture.resp.AuthResp;

public interface AuthService {
    CommonResult<String> register(RegisterReq req);

    CommonResult<AuthResp> login(LoginReq req);

    CommonResult<String> sendCode(SendCodeReq req);
}
