package com.gachiga.ride;

import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;
import com.gachiga.ride.dto.CreateRideRequestRequest;
import com.gachiga.ride.dto.RideRequestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매칭 요청 API (PRD §8).
 *
 * <p>모든 응답은 {@link ApiResponse} 로 감싼다. 현재 사용자는 {@link CurrentUser} 로 받는다 —
 * Phase 0 에는 {@code X-Dev-User} 헤더, Phase 1 부터는 JWT 에서 온다.
 */
@Tag(name = "requests", description = "매칭 요청·대기열 (이승민)")
@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RideRequestController {

    private final RideRequestService rideRequestService;

    @Operation(
            summary = "[P0] 매칭 요청 생성 (대기열 등록)",
            description =
                    "대기열에 들어간다 (FR-07·FR-08). 진행 중인 요청이 있으면 ALREADY_IN_QUEUE, "
                            + "거점에서 500m 미만이면 REQUEST_TOO_SHORT, 이용 제한 계정이면 USER_SUSPENDED 가 난다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RideRequestResponse> create(
            @CurrentUser Long userId, @Valid @RequestBody CreateRideRequestRequest request) {
        return ApiResponse.ok(rideRequestService.create(userId, request));
    }

    @Operation(
            summary = "[P0] 내 진행 중 요청 조회",
            description =
                    "대기 화면이 쓰는 엔드포인트다 (FR-09). 남은 시간과 같은 거점 후보 수가 함께 나온다. "
                            + "진행 중인 요청이 없으면 data 가 null 이다 — 404 가 아니다.")
    @GetMapping("/me")
    public ApiResponse<RideRequestResponse> findMine(@CurrentUser Long userId) {
        return ApiResponse.ok(rideRequestService.findMyRequest(userId).orElse(null));
    }

    @Operation(
            summary = "[P0] 요청 취소",
            description =
                    "본인 요청만 취소할 수 있다 (FR-08). 이미 확정된 그룹에서 빠지는 것은 "
                            + "POST /api/groups/{groupId}/reject 다.")
    @DeleteMapping("/{requestId}")
    public ApiResponse<Void> cancel(@CurrentUser Long userId, @PathVariable Long requestId) {
        rideRequestService.cancel(userId, requestId);
        return ApiResponse.ok();
    }
}
