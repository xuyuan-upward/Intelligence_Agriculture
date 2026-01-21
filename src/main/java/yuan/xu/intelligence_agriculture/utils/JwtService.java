package yuan.xu.intelligence_agriculture.utils;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import yuan.xu.intelligence_agriculture.dto.AuthUser;
import yuan.xu.intelligence_agriculture.model.SysUser;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtService {
    @Value("${auth.jwt-secret}")
    private String jwtSecret;

    @Value("${auth.jwt-expire-minutes}")
    private Long jwtExpireMinutes;

    public String createToken(SysUser user) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId());
        payload.put("phone", user.getPhone());
        payload.put("role", user.getRole());
        payload.put("exp", System.currentTimeMillis() + jwtExpireMinutes * 60_000L);
        return JWTUtil.createToken(payload, jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthUser parseToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        boolean valid = JWTUtil.verify(token, jwtSecret.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            return null;
        }
        JWT jwt = JWTUtil.parseToken(token);
        Object expObj = jwt.getPayload("exp");
        long exp = toLong(expObj);
        if (exp > 0 && System.currentTimeMillis() > exp) {
            return null;
        }
        Long userId = toLongObj(jwt.getPayload("userId"));
        String phone = String.valueOf(jwt.getPayload("phone"));
        String role = String.valueOf(jwt.getPayload("role"));
        if (userId == null) {
            return null;
        }
        return new AuthUser(userId, phone, role);
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private Long toLongObj(Object value) {
        long result = toLong(value);
        return result == 0L ? null : result;
    }
}
