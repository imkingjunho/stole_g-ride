package com.gachiga.stats;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** 서버에 등록한 기존 회원만 운영 데이터를 열 수 있다. */
@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final AdminDashboardService dashboard;
    private final Set<Long> operators;

    public AdminDashboardController(
            AdminDashboardService dashboard,
            @Value("${gachiga.admin.user-ids}") Set<Long> operators) {
        this.dashboard = dashboard;
        this.operators = Set.copyOf(operators);
    }

    @GetMapping("/access")
    public ApiResponse<Boolean> access(@CurrentUser Long userId) {
        return ApiResponse.ok(operators.contains(userId));
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboard> dashboard(@CurrentUser Long userId) {
        if (!operators.contains(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        return ApiResponse.ok(dashboard.snapshot());
    }
}
