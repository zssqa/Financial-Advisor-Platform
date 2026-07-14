package com.finance.advisor.portfolio;

import java.util.ArrayList;
import java.util.List;

/**
 * 资产导入结果：成功条数、解析成功的资产列表、解析失败的行信息。
 */
public class AssetImportResult {

    private int success;
    private List<Asset> assets = new ArrayList<>();
    private List<FailedRow> failed = new ArrayList<>();

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public List<Asset> getAssets() {
        return assets;
    }

    public void setAssets(List<Asset> assets) {
        this.assets = assets;
    }

    public List<FailedRow> getFailed() {
        return failed;
    }

    public void setFailed(List<FailedRow> failed) {
        this.failed = failed;
    }

    /**
     * 解析失败的行信息。row 为数据所在行号（含表头行，从 1 开始计数）。
     */
    public static class FailedRow {
        private int row;
        private String reason;

        public FailedRow() {
        }

        public FailedRow(int row, String reason) {
            this.row = row;
            this.reason = reason;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
