package yuan.xu.intelligence_agriculture.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import yuan.xu.intelligence_agriculture.dto.AuthUser;
import yuan.xu.intelligence_agriculture.dto.CommonResult;
import yuan.xu.intelligence_agriculture.model.SysUser;
import yuan.xu.intelligence_agriculture.req.UpdateRoleReq;
import yuan.xu.intelligence_agriculture.resp.UserResp;
import yuan.xu.intelligence_agriculture.service.SysUserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/agriculture/admin/users")
@CrossOrigin(origins = "*")
public class UserAdminController {

    @Autowired
    private SysUserService sysUserService;

    @GetMapping
    public CommonResult<List<UserResp>> listUsers() {
        List<SysUser> list = sysUserService.lambdaQuery().orderByDesc(SysUser::getCreateTime).list();
        List<UserResp> resp = list.stream()
                .map(u -> new UserResp(u.getId(), u.getPhone(), u.getUsername(), u.getRole(), u.getCreateTime()))
                .collect(Collectors.toList());
        return CommonResult.success(resp);
    }

    @PostMapping("/role")
    public CommonResult<String> updateRole(@RequestBody UpdateRoleReq req, HttpServletRequest request, HttpServletResponse response) {
        AuthUser currentUser = (AuthUser) request.getAttribute("authUser");
        if (currentUser == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return CommonResult.failed("未授权访问");
        }

        if (req.getUserId() == null) {
            return CommonResult.failed("参数错误");
        }

        // 1. 普通用户不能修改任何人的角色（拦截器已处理，这里是二次校验）
        if (!"ADMIN".equals(currentUser.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return CommonResult.failed("权限不足：只有管理员可以修改角色");
        }

        // 2. 不能修改自己的角色
        if (currentUser.getUserId().equals(req.getUserId())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return CommonResult.failed("操作拒绝：不能修改自己的角色");
        }

        // 查询目标用户
        SysUser targetUser = sysUserService.getById(req.getUserId());
        if (targetUser == null) {
            return CommonResult.failed("目标用户不存在");
        }

        // 3. 管理员不能修改其他管理员的角色
        if ("ADMIN".equals(targetUser.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return CommonResult.failed("权限不足：不能修改其他管理员的角色");
        }

        if (!"ADMIN".equals(req.getRole()) && !"USER".equals(req.getRole())) {
            return CommonResult.failed("角色不合法");
        }

        boolean updated = sysUserService.lambdaUpdate()
                .eq(SysUser::getId, req.getUserId())
                .set(SysUser::getRole, req.getRole())
                .update();

        if (!updated) {
            return CommonResult.failed("更新失败");
        }
        return CommonResult.success("OK");
    }
}
