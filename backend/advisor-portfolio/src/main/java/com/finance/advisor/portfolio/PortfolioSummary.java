package com.finance.advisor.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资产组合汇总。
 */
public class PortfolioSummary {

    private BigDecimal totalCost;
    private BigDecimal totalMarketValue;
    private BigDecimal profitLoss;
    private List<TypeBreakdown> breakdown;

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public BigDecimal getTotalMarketValue() {
        return totalMarketValue;
    }

    public void setTotalMarketValue(BigDecimal totalMarketValue) {
        this.totalMarketValue = totalMarketValue;
    }

    public BigDecimal getProfitLoss() {
        return profitLoss;
    }

    public void setProfitLoss(BigDecimal profitLoss) {
        this.profitLoss = profitLoss;
    }

    public List<TypeBreakdown> getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(List<TypeBreakdown> breakdown) {
        this.breakdown = breakdown;
    }

    /**
     * 按 type 分组的汇总项。
     */
    public static class TypeBreakdown {

        private String type;
        private BigDecimal cost;
        private BigDecimal marketValue;
        private BigDecimal percentage;

        public TypeBreakdown() {
        }

        public TypeBreakdown(String type, BigDecimal cost, BigDecimal marketValue, BigDecimal percentage) {
            this.type = type;
            this.cost = cost;
            this.marketValue = marketValue;
            this.percentage = percentage;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public BigDecimal getCost() {
            return cost;
        }

        public void setCost(BigDecimal cost) {
            this.cost = cost;
        }

        public BigDecimal getMarketValue() {
            return marketValue;
        }

        public void setMarketValue(BigDecimal marketValue) {
            this.marketValue = marketValue;
        }

        public BigDecimal getPercentage() {
            return percentage;
        }

        public void setPercentage(BigDecimal percentage) {
            this.percentage = percentage;
        }
    }
}
