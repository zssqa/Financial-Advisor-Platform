package com.finance.advisor.portfolio.goal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 财务目标数据访问层（JdbcTemplate）。
 */
@Repository
public class GoalRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<Goal> GOAL_ROW_MAPPER = (rs, rowNum) -> {
        Goal goal = new Goal();
        goal.setId(rs.getLong("id"));
        goal.setUserId(rs.getLong("user_id"));
        goal.setType(rs.getString("type"));
        goal.setTargetAmount(rs.getBigDecimal("target_amount"));
        goal.setCurrentAmount(rs.getBigDecimal("current_amount"));
        java.sql.Date deadline = rs.getDate("deadline");
        goal.setDeadline(deadline != null ? deadline.toLocalDate() : null);
        goal.setMonthlyContribution(rs.getBigDecimal("monthly_contribution"));
        goal.setNotes(rs.getString("notes"));
        return goal;
    };

    public GoalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Goal> findByUserId(Long userId) {
        String sql = "SELECT id, user_id, type, target_amount, current_amount, deadline, monthly_contribution, notes "
                + "FROM goals WHERE user_id = ? ORDER BY id";
        return jdbcTemplate.query(sql, GOAL_ROW_MAPPER, userId);
    }

    public Optional<Goal> findById(Long id) {
        String sql = "SELECT id, user_id, type, target_amount, current_amount, deadline, monthly_contribution, notes "
                + "FROM goals WHERE id = ?";
        List<Goal> list = jdbcTemplate.query(sql, GOAL_ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Goal save(Goal goal) {
        String sql = "INSERT INTO goals (user_id, type, target_amount, current_amount, deadline, "
                + "monthly_contribution, notes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            // 显式指定只返回 id 列，避免 PostgreSQL 驱动返回整行导致 KeyHolder.getKey() 抛 InvalidDataAccessApiUsageException
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, goal.getUserId());
            ps.setString(2, goal.getType());
            setNullableBigDecimal(ps, 3, goal.getTargetAmount());
            setNullableBigDecimal(ps, 4, goal.getCurrentAmount());
            setNullableDate(ps, 5, goal.getDeadline());
            setNullableBigDecimal(ps, 6, goal.getMonthlyContribution());
            setNullableString(ps, 7, goal.getNotes());
            ps.setLong(8, System.currentTimeMillis());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            goal.setId(key.longValue());
        }
        return goal;
    }

    public int update(Goal goal) {
        String sql = "UPDATE goals SET type = ?, target_amount = ?, current_amount = ?, deadline = ?, "
                + "monthly_contribution = ?, notes = ? WHERE id = ? AND user_id = ?";
        return jdbcTemplate.update(sql,
                goal.getType(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                goal.getDeadline(),
                goal.getMonthlyContribution(),
                goal.getNotes(),
                goal.getId(),
                goal.getUserId());
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM goals WHERE id = ?", id);
    }

    public boolean existsByIdAndUserId(Long id, Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM goals WHERE id = ? AND user_id = ?",
                Integer.class, id, userId);
        return count != null && count > 0;
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int index, BigDecimal value)
            throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private static void setNullableDate(PreparedStatement ps, int index, LocalDate value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, java.sql.Date.valueOf(value));
        }
    }
}
