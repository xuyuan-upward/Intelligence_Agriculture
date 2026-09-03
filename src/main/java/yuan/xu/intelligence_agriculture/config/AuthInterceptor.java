package yuan.xu.intelligence_agriculture.config;

import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import yuan.xu.intelligence_agriculture.dto.AuthUser;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.utils.JwtService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;

    public AuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        AuthUser authUser = jwtService.parseToken(token);
        if (authUser == null) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, CommonResult.failed("Unauthorized"));
            return false;
        }
        request.setAttribute("authUser", authUser);
        String path = request.getRequestURI();
        // /agriculture/admin 路径要求 SUPERADMIN 或 ADMIN 角色
        // 但 /info 端点允许所有已登录用户访问（用于刷新时同步自己的角色信息）
        if (path.startsWith("/agriculture/admin")
                && !path.endsWith("/info")
                && !"ADMIN".equals(authUser.getRole())
                && !"SUPERADMIN".equals(authUser.getRole())) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, CommonResult.failed("Forbidden"));
            return false;
        }
        return true;
    }

    private void writeJson(HttpServletResponse response, int status, CommonResult<?> result) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(result));
    }
}
