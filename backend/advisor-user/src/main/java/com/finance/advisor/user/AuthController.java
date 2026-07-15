package com.finance.advisor.user;

import com.finance.advisor.common.dto.ApiResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 / 用户接口
 *
 * - POST /api/auth/register  注册（201，重复用户名 409）
 * - POST /api/auth/login     登录（200，凭证错误 401）
 * - GET  /api/auth/profile   获取当前用户信息（需 token）
 * - PUT  /api/auth/risk-level 更新当前用户风险等级（需 token）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody RegisterRequest request) {
        User user = userService.register(request.getUsername(), request.getPassword());
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        AuthResponse body = new AuthResponse(token, user.getId(), user.getUsername(), user.getRiskLevel());
        return ApiResponse.success(body);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        User user = userService.login(request.getUsername(), request.getPassword());
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        AuthResponse body = new AuthResponse(token, user.getId(), user.getUsername(), user.getRiskLevel());
        return ApiResponse.success(body);
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfile> profile() {
        User user = userService.findById(currentUserId());
        return ApiResponse.success(toProfile(user));
    }

    @PutMapping("/risk-level")
    public ApiResponse<UserProfile> updateRiskLevel(@RequestBody RiskLevelRequest request) {
        User user = userService.updateRiskLevel(currentUserId(), request.getRiskLevel());
        return ApiResponse.success(toProfile(user));
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("当前会话未携带有效用户标识");
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getRiskLevel(), user.getCreatedAt());
    }
}
