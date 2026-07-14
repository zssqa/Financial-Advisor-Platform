package com.finance.advisor.portfolio.goal;

import java.math.BigDecimal;
import java.util.List;

/**
 * 财务目标汇总，包含每个目标的进度计算结果。
 */
public class GoalSummary {

    private List<GoalProgress> goals;

    public List<GoalProgress> getGoals() {
        return goals;
    }

    public void setGoals(List<GoalProgress> goals) {
        this.goals = goals;
    }

    /**
     * 单个目标的进度信息。
     */
    public static class GoalProgress {

        private Goal goal;
        private BigDecimal progressPercent;
        private BigDecimal remainingAmount;
        private long monthsRemaining;
        private BigDecimal monthlyNeeded;

        public GoalProgress() {
        }

        public GoalProgress(Goal goal, BigDecimal progressPercent, BigDecimal remainingAmount,
                            long monthsRemaining, BigDecimal monthlyNeeded) {
            this.goal = goal;
            this.progressPercent = progressPercent;
            this.remainingAmount = remainingAmount;
            this.monthsRemaining = monthsRemaining;
            this.monthlyNeeded = monthlyNeeded;
        }

        public Goal getGoal() {
            return goal;
        }

        public void setGoal(Goal goal) {
            this.goal = goal;
        }

        public BigDecimal getProgressPercent() {
            return progressPercent;
        }

        public void setProgressPercent(BigDecimal progressPercent) {
            this.progressPercent = progressPercent;
        }

        public BigDecimal getRemainingAmount() {
            return remainingAmount;
        }

        public void setRemainingAmount(BigDecimal remainingAmount) {
            this.remainingAmount = remainingAmount;
        }

        public long getMonthsRemaining() {
            return monthsRemaining;
        }

        public void setMonthsRemaining(long monthsRemaining) {
            this.monthsRemaining = monthsRemaining;
        }

        public BigDecimal getMonthlyNeeded() {
            return monthlyNeeded;
        }

        public void setMonthlyNeeded(BigDecimal monthlyNeeded) {
            this.monthlyNeeded = monthlyNeeded;
        }
    }
}
