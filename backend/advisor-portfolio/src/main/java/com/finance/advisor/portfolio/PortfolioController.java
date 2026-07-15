package com.finance.advisor.portfolio;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.portfolio.AssetImportResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资产组合 REST 接口。所有接口均需登录（SecurityConfig 已要求 /api/** 认证）。
 */
@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PriceAlertService priceAlertService;

    public PortfolioController(PortfolioService portfolioService, PriceAlertService priceAlertService) {
        this.portfolioService = portfolioService;
        this.priceAlertService = priceAlertService;
    }

    @GetMapping
    public ApiResponse<List<Asset>> list() {
        return ApiResponse.success(portfolioService.list(SecurityUtil.currentUserId()));
    }

    @PostMapping
    public ApiResponse<Asset> create(@RequestBody Asset asset) {
        return ApiResponse.success(portfolioService.create(SecurityUtil.currentUserId(), asset));
    }

    @PutMapping("/{id}")
    public ApiResponse<Asset> update(@PathVariable Long id, @RequestBody Asset asset) {
        try {
            asset.setId(id);
            return ApiResponse.success(portfolioService.update(SecurityUtil.currentUserId(), asset));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            portfolioService.delete(SecurityUtil.currentUserId(), id);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        Long userId = SecurityUtil.currentUserId();
        PortfolioSummary summary = portfolioService.summary(userId);
        // 在 summary 返回中追加 unreadAlerts 字段（未读已触发预警数）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalCost", summary.getTotalCost());
        data.put("totalMarketValue", summary.getTotalMarketValue());
        data.put("profitLoss", summary.getProfitLoss());
        data.put("breakdown", summary.getBreakdown());
        data.put("unreadAlerts", priceAlertService.countUnreadAlerts(userId));
        return ApiResponse.success(data);
    }

    /**
     * 查询当前用户资产组合最近 N 天的每日汇总市值，供 Dashboard 折线图使用。
     * 默认 30 天。
     */
    @GetMapping("/history")
    public ApiResponse<List<Map<String, Object>>> history(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(portfolioService.getPortfolioHistory(SecurityUtil.currentUserId(), days));
    }

    @PostMapping(":import")
    public ApiResponse<AssetImportResult> importAssets(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error(400, "文件为空");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            return ApiResponse.error(400, "文件大小超过10MB限制");
        }
        String filename = file.getOriginalFilename();
        String ext = filename != null && filename.contains(".")
                ? filename.substring(filename.lastIndexOf(".") + 1).toLowerCase()
                : "";
        if (!Set.of("xlsx", "xls", "csv").contains(ext)) {
            return ApiResponse.error(400, "仅支持 xlsx/xls/csv 格式");
        }
        try {
            return ApiResponse.success(portfolioService.importAssets(SecurityUtil.currentUserId(), file));
        } catch (Exception e) {
            return ApiResponse.error(500, "导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("资产导入模板");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            String[] headers = {"类型", "代码", "名称", "数量", "成本价", "买入日期", "备注"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            // 示例行
            org.apache.poi.ss.usermodel.Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("股票");
            example.createCell(1).setCellValue("sh600036");
            example.createCell(2).setCellValue("招商银行");
            example.createCell(3).setCellValue(100);
            example.createCell(4).setCellValue(10.50);
            example.createCell(5).setCellValue("2026-01-15");
            example.createCell(6).setCellValue("长期持有");
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            workbook.write(bos);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=asset_import_template.xlsx")
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .body(bos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("生成模板失败: " + e.getMessage());
        }
    }
}
