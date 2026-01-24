package yuan.xu.intelligence_agriculture.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.req.LoginReq;
import yuan.xu.intelligence_agriculture.req.RegisterReq;
import yuan.xu.intelligence_agriculture.req.ResetPasswordReq;
import yuan.xu.intelligence_agriculture.req.SendCodeReq;
import yuan.xu.intelligence_agriculture.resp.AuthResp;
import yuan.xu.intelligence_agriculture.service.AuthService;

import javax.servlet.http.HttpServletResponse;


@RestController
@RequestMapping("/agriculture/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/send-code")
    public CommonResult<String> sendCode(@RequestBody SendCodeReq req) {
        return authService.sendCode(req);
    }

    @PostMapping("/register")
    public CommonResult<String> register(@RequestBody RegisterReq req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public CommonResult<AuthResp> login(@RequestBody LoginReq req, HttpServletResponse response) {
        CommonResult<AuthResp> result = authService.login(req);
        if (result.getCode() != 200) {
            response.setStatus((int) result.getCode());
        }
        return result;
    }

    @PostMapping("/reset-password")
    public CommonResult<String> resetPassword(@RequestBody ResetPasswordReq req) {
        return authService.resetPassword(req);
    }
}
