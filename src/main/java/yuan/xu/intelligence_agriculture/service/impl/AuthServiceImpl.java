package yuan.xu.intelligence_agriculture.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.model.SysUser;
import yuan.xu.intelligence_agriculture.req.LoginReq;
import yuan.xu.intelligence_agriculture.req.RegisterReq;
import yuan.xu.intelligence_agriculture.req.SendCodeReq;
import yuan.xu.intelligence_agriculture.resp.AuthResp;
import yuan.xu.intelligence_agriculture.service.AuthService;
import yuan.xu.intelligence_agriculture.service.SysUserService;
import yuan.xu.intelligence_agriculture.utils.JwtService;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.regex.Pattern;

import static yuan.xu.intelligence_agriculture.key.RedisKey.AUTH_SMS_CODE_KEY;
import static yuan.xu.intelligence_agriculture.key.RedisKey.AUTH_SMS_COOLDOWN_KEY;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public CommonResult<String> register(RegisterReq req) {
        String phone = StringUtils.trimToEmpty(req.getPhone());
        if (!isValidPhone(phone)) {
            return CommonResult.failed("手机号格式不正确");
        }
        if (StringUtils.isBlank(req.getUsername())) {
            req.setUsername("user_" + phone.substring(phone.length() - 6));
        }
        if (StringUtils.isBlank(req.getPassword()) || req.getPassword().length() < 6) {
            return CommonResult.failed("密码至少6位");
        }
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            return CommonResult.failed("两次密码不一致");
        }
        SysUser exists = sysUserService.lambdaQuery().eq(SysUser::getPhone, phone).one();
        if (exists != null) {
            return CommonResult.failed("手机号已注册");
        }
        String finalUsername = ensureUniqueUsername(req.getUsername());
        if (!validateCode(phone, req.getCode())) {
            return CommonResult.failed("验证码无效或已过期");
        }
        SysUser user = new SysUser();
        user.setPhone(phone);
        user.setUsername(finalUsername);
        user.setPassword(BCrypt.hashpw(req.getPassword()));
        user.setRole("USER");
        user.setCreateTime(new Date());
        sysUserService.save(user);
        return CommonResult.success("OK");
    }

    // 预生成的 dummy hash，用于防止计时攻击 (密码为 "dummy_password")
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgNIvDg23nI7J9G.9mE8.I46U7oK";

    @Override
    public CommonResult<AuthResp> login(LoginReq req) {
        String phone = StringUtils.trimToEmpty(req.getPhone());
        if (!isValidPhone(phone)) {
            return new CommonResult<>(400, "手机号格式不正确", null);
        }

        SysUser user = sysUserService.lambdaQuery().eq(SysUser::getPhone, phone).one();
        
        if ("password".equalsIgnoreCase(req.getLoginType())) {
            String password = req.getPassword();
            boolean userExists = (user != null);
            
            // 无论用户是否存在，都执行 checkpw 以防止计时攻击
            boolean passwordMatch = BCrypt.checkpw(
                StringUtils.defaultString(password), 
                userExists ? user.getPassword() : DUMMY_HASH
            );

            if (!userExists) {
                return new CommonResult<>(404, "该手机号未注册，请检查输入或注册新账号", null);
            }
            if (!passwordMatch) {
                return new CommonResult<>(401, "密码不正确，请重新输入", null);
            }
        } else if ("code".equalsIgnoreCase(req.getLoginType())) {
            if (user == null) {
                return new CommonResult<>(404, "该手机号未注册，请检查输入或注册新账号", null);
            }
            if (!validateCode(phone, req.getCode())) {
                return new CommonResult<>(401, "验证码无效或已过期", null);
            }
        } else {
            return CommonResult.failed("登录方式不支持");
        }

        String token = jwtService.createToken(user);
        return CommonResult.success(new AuthResp(token, user.getRole(), user.getId(), user.getUsername()));
    }

    @Override
    public CommonResult<String> sendCode(SendCodeReq req) {
        String phone = StringUtils.trimToEmpty(req.getPhone());
        if (!isValidPhone(phone)) {
            return CommonResult.failed("手机号格式不正确");
        }
        String cooldownKey = AUTH_SMS_COOLDOWN_KEY + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            return CommonResult.failed("请求过于频繁");
        }
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        redisTemplate.opsForValue().set(AUTH_SMS_CODE_KEY + phone, code, 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(cooldownKey, 1, 60, TimeUnit.SECONDS);
        log.info("验证码发送: phone:{} code:{}", phone, code);
        return CommonResult.success("OK");
    }

    private boolean isValidPhone(String phone) {
        return StringUtils.isNotBlank(phone) && PHONE_PATTERN.matcher(phone).matches();
    }

    private boolean validateCode(String phone, String code) {
        if (StringUtils.isBlank(code)) {
            return false;
        }
        Object stored = redisTemplate.opsForValue().get(AUTH_SMS_CODE_KEY + phone);
        if (stored == null) {
            return false;
        }
        return code.equals(String.valueOf(stored));
    }

    private String ensureUniqueUsername(String base) {
        String candidate = base;
        int suffix = 1;
        while (sysUserService.lambdaQuery().eq(SysUser::getUsername, candidate).one() != null) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }
}
