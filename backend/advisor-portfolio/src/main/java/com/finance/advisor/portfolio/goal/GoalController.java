package com.finance.advisor.portfolio.goal;

import com.finance.advisor.common.dto.ApiResponse;
import com.finance.advisor.portfolio.SecurityUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 财务目标 REST 接口。所有接口均需登录。
 */
@RestController
@RequestMapping("/api/goal")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/list")
    public ApiResponse<List<Goal>> list() {
        return ApiResponse.success(goalService.list(SecurityUtil.currentUserId()));
    }

    @PostMapping
    public ApiResponse<Goal> create(@RequestBody Goal goal) {
        return ApiResponse.success(goalService.create(SecurityUtil.currentUserId(), goal));
    }

    @PutMapping("/{id}")
    public ApiResponse<Goal> update(@PathVariable Long id, @RequestBody Goal goal) {
        try {
            goal.setId(id);
            return ApiResponse.success(goalService.update(SecurityUtil.currentUserId(), goal));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            goalService.delete(SecurityUtil.currentUserId(), id);
            return ApiResponse.success(null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @GetMapping("/summary")
    public ApiResponse<GoalSummary> summary() {
        return ApiResponse.success(goalService.summary(SecurityUtil.currentUserId()));
    }
}
