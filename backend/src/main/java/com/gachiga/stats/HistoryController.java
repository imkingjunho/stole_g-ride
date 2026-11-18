package com.gachiga.stats;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;
import com.gachiga.contract.matching.RideHistoryPort;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** 개인 완료 내역과 통계. 사용자 ID는 인증 Principal에서만 받는다. */
@RestController
@RequiredArgsConstructor
public class HistoryController {
    private final RideHistoryPort history;

    @GetMapping("/api/history")
    public ApiResponse<RideHistoryPort.HistoryPage> history(
            @CurrentUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String yearMonth) {
        if (page < 0 || size < 1 || size > 50) throw new BusinessException(ErrorCode.INVALID_INPUT);
        YearMonth month = null;
        if (yearMonth != null) {
            try {
                if (!yearMonth.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
                    throw new DateTimeParseException("월 형식", yearMonth, 0);
                month = YearMonth.parse(yearMonth);
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 월은 YYYY-MM 형식입니다.");
            }
        }
        return ApiResponse.ok(
                history.history(
                        userId,
                        month == null ? null : month.atDay(1).atStartOfDay(),
                        month == null ? null : month.plusMonths(1).atDay(1).atStartOfDay(),
                        page,
                        size));
    }

    @GetMapping("/api/stats/me")
    public ApiResponse<RideHistoryPort.MyStats> stats(@CurrentUser Long userId) {
        return ApiResponse.ok(history.stats(userId));
    }
}
