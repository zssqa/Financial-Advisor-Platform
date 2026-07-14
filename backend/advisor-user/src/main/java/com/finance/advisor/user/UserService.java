package com.finance.advisor.user;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 用户服务：注册、登录、查询、风险等级更新。
 *
 * 启动时自动建表（复用项目现有“启动自动建表”模式，参考 DocumentMetadataService）。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final String DEFAULT_RISK_LEVEL = "R3";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        initializeSchema();
    }

    private void initializeSchema() {
        try {
            userRepository.createTableIfNotExists();
        } catch (Exception e) {
            log.warn("初始化 users 表失败: {}", e.getMessage());
        }
    }

    /**
     * 注册新用户：BCrypt 加密密码，默认风险等级 R3，用户名重复抛 409 冲突。
     */
    public User register(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRiskLevel(DEFAULT_RISK_LEVEL);
        user.setCreatedAt(System.currentTimeMillis());
        return userRepository.save(user);
    }

    /**
     * 登录：校验密码，成功返回 User，凭证错误抛 401。
     */
    public User login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return user;
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    public User updateRiskLevel(Long userId, String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "风险等级不能为空");
        }
        findById(userId); // 确保用户存在，不存在抛 404
        userRepository.updateRiskLevel(userId, riskLevel);
        return findById(userId);
    }
}
