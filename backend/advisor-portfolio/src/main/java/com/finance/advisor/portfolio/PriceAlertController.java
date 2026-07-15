package com.finance.advisor.portfolio;

import com.finance.advisor.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 价格预警 REST 接口。所有接口均需登录（SecurityConfig 已要求 /api/** 认证）。
 */
@RestController
@RequestMapping("/api/alerts")
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    public PriceAlertController(PriceAlertService priceAlertService) {
        this.priceAlertService = priceAlertService;
    }

    /**
     * 创建价格预警。
     * 请求体：{ "symbol": "...", "assetName": "...", "alertType": "above|below", "thresholdPrice": 10.5 }
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String symbol = body.get("symbol") != null ? body.get("symbol").toString() : null;
        String assetName = body.get("assetName") != null ? body.get("assetName").toString() : null;
        String alertType = body.get("alertType") != null ? body.get("alertType").toString() : null;
        Object threshold = body.get("thresholdPrice");

        if (symbol == null || symbol.isBlank()) {
            return ApiResponse.error(400, "symbol 不能为空");
        }
        if (alertType == null || (!"above".equals(alertType) && !"below".equals(alertType))) {
            return ApiResponse.error(400, "alertType 必须为 above 或 below");
        }
        if (threshold == null) {
            return ApiResponse.error(400, "thresholdPrice 不能为空");
        }

        BigDecimal thresholdPrice;
        try {
            thresholdPrice = new BigDecimal(threshold.toString());
        } catch (NumberFormatException e) {
            return ApiResponse.error(400, "thresholdPrice 格式无效");
        }

        return ApiResponse.success(
                priceAlertService.createAlert(SecurityUtil.currentUserId(), symbol, assetName, alertType, thresholdPrice)
        );
    }

    /**
     * 查询当前用户预警列表。
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(priceAlertService.listAlerts(SecurityUtil.currentUserId()));
    }

    /**
     * 删除预警。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        priceAlertService.deleteAlert(id, SecurityUtil.currentUserId());
        return ApiResponse.success(null);
    }

    /**
     * 标记预警已读。
     * 请求体：{ "read": true }
     */
    @PatchMapping("/{id}")
    public ApiResponse<Void> markAsRead(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object readFlag = body != null ? body.get("read") : null;
        if (!Boolean.TRUE.equals(readFlag)) {
            return ApiResponse.success(null);
        }
        priceAlertService.markAsRead(id, SecurityUtil.currentUserId());
        return ApiResponse.success(null);
    }
}
