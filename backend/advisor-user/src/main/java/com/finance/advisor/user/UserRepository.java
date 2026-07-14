package com.finance.advisor.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层（基于 JdbcTemplate，不使用 JPA）
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<User> USER_ROW_MAPPER = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRiskLevel(rs.getString("risk_level"));
        user.setCreatedAt(rs.getLong("created_at"));
        return user;
    };

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动时自动建表（IF NOT EXISTS）。
     */
    public void createTableIfNotExists() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id BIGSERIAL PRIMARY KEY,
                username VARCHAR(64) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                risk_level VARCHAR(8) DEFAULT 'R3',
                created_at BIGINT NOT NULL
            )
            """);
    }

    public Optional<User> findByUsername(String username) {
        List<User> users = jdbcTemplate.query(
                "SELECT id, username, password_hash, risk_level, created_at FROM users WHERE username = ?",
                USER_ROW_MAPPER, username);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public Optional<User> findById(Long id) {
        List<User> users = jdbcTemplate.query(
                "SELECT id, username, password_hash, risk_level, created_at FROM users WHERE id = ?",
                USER_ROW_MAPPER, id);
        return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username);
        return count != null && count > 0;
    }

    public User save(User user) {
        String sql = "INSERT INTO users (username, password_hash, risk_level, created_at) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            // 显式指定只返回 id 列，避免 PostgreSQL 驱动返回整行导致 KeyHolder.getKey() 抛 InvalidDataAccessApiUsageException
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRiskLevel());
            ps.setLong(4, user.getCreatedAt());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            user.setId(key.longValue());
        }
        return user;
    }

    public int updateRiskLevel(Long userId, String riskLevel) {
        return jdbcTemplate.update(
                "UPDATE users SET risk_level = ? WHERE id = ?", riskLevel, userId);
    }
}
