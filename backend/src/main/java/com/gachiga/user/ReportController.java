package com.gachiga.user;

import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;
import com.gachiga.user.dto.ReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 신고 API (PRD §8). */
@Tag(name = "users", description = "신고 (임승현)")
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(
            operationId = "postReports",
            summary = "[P2] 신고",
            description =
                    "같은 그룹이었던 사람만 신고할 수 있다 (FR-04). 누적 3회면 7일 이용이 제한된다. "
                            + "그룹 구성원이 아니면 GROUP_NOT_MEMBER.")
    @PostMapping
    public ApiResponse<Void> report(
            @CurrentUser Long userId, @Valid @RequestBody ReportRequest request) {
        reportService.report(userId, request);
        return ApiResponse.ok();
    }
}
