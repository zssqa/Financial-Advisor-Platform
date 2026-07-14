package com.finance.advisor.portfolio.goal;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 财务目标服务：CRUD + 进度汇总。
 */
@Service
public class GoalService {

    private static final Logger log = LoggerFactory.getLogger(GoalService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final GoalRepository goalRepository;
    private final JdbcTemplate jdbcTemplate;

    public GoalService(GoalRepository goalRepository, JdbcTemplate jdbcTemplate) {
        this.goalRepository = goalRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS goals (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    type VARCHAR(24) NOT NULL,
                    target_amount NUMERIC(20,2),
                    current_amount NUMERIC(20,2),
                    deadline DATE,
                    monthly_contribution NUMERIC(20,2),
                    notes TEXT,
                    created_at BIGINT NOT NULL
                )
                """);
        log.info("goals 表已就绪");
    }

    public List<Goal> list(Long userId) {
        return goalRepository.findByUserId(userId);
    }

    public Goal create(Long userId, Goal goal) {
        goal.setId(null);
        goal.setUserId(userId);
        return goalRepository.save(goal);
    }

    public Goal update(Long userId, Goal goal) {
        if (goal.getId() == null || !goalRepository.existsByIdAndUserId(goal.getId(), userId)) {
            throw new IllegalArgumentException("目标不存在或无权操作");
        }
        goal.setUserId(userId);
        goalRepository.update(goal);
        return goalRepository.findById(goal.getId()).orElse(goal);
    }

    public void delete(Long userId, Long id) {
        if (!goalRepository.existsByIdAndUserId(id, userId)) {
            throw new IllegalArgumentException("目标不存在或无权操作");
        }
        goalRepository.deleteById(id);
    }

    public GoalSummary summary(Long userId) {
        List<Goal> goals = goalRepository.findByUserId(userId);
        List<GoalSummary.GoalProgress> progressList = new ArrayList<>();
        LocalDate now = LocalDate.now();

        for (Goal goal : goals) {
            BigDecimal target = nz(goal.getTargetAmount());
            BigDecimal current = nz(goal.getCurrentAmount());

            BigDecimal remainingAmount = target.subtract(current);
            if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
                remainingAmount = BigDecimal.ZERO;
            }

            BigDecimal progressPercent = target.compareTo(BigDecimal.ZERO) != 0
                    ? current.multiply(HUNDRED).divide(target, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            long monthsRemaining = 0;
            if (goal.getDeadline() != null && goal.getDeadline().isAfter(now)) {
                monthsRemaining = ChronoUnit.MONTHS.between(now, goal.getDeadline());
                if (monthsRemaining < 0) {
                    monthsRemaining = 0;
                }
            }

            BigDecimal monthlyNeeded;
            if (monthsRemaining > 0) {
                monthlyNeeded = remainingAmount.divide(BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.HALF_UP);
            } else {
                monthlyNeeded = goal.getMonthlyContribution() != null
                        ? goal.getMonthlyContribution() : BigDecimal.ZERO;
            }

            progressList.add(new GoalSummary.GoalProgress(goal, progressPercent, remainingAmount,
                    monthsRemaining, monthlyNeeded));
        }

        GoalSummary summary = new GoalSummary();
        summary.setGoals(progressList);
        return summary;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
