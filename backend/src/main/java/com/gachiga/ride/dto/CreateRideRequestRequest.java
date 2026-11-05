package com.gachiga.ride.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 매칭 요청 생성 본문. {@code docs/api-spec.yaml} 의 {@code CreateRideRequest} 와 1:1 이다.
 *
 * <p>{@code maxWaitMin} 이 허용 목록(5·10·15·20)에 있는지는 설정값과 대조해야 하므로
 * 애너테이션이 아니라 서비스에서 검사한다.
 *
 * @param departAt 희망 출발 시각. <b>오프셋 없는 ISO 문자열</b>이다. {@code 2026-10-20T08:30:00}
 */
public record CreateRideRequestRequest(
        @NotNull(message = "출발 거점을 선택해 주세요") Long hubId,
        @NotBlank(message = "목적지를 입력해 주세요")
                @Size(max = 100, message = "목적지 이름이 너무 깁니다")
                String destName,
        @NotNull(message = "목적지 위도가 필요합니다")
                @DecimalMin(value = "-90.0")
                @DecimalMax(value = "90.0")
                Double destLat,
        @NotNull(message = "목적지 경도가 필요합니다")
                @DecimalMin(value = "-180.0")
                @DecimalMax(value = "180.0")
                Double destLng,
        @NotNull(message = "희망 출발 시각이 필요합니다") LocalDateTime departAt,
        @NotNull(message = "최대 대기 시간을 선택해 주세요") Integer maxWaitMin,
        @NotNull(message = "동성 매칭 여부를 지정해 주세요") Boolean sameGenderOnly,
        @NotNull(message = "최대 우회율을 선택해 주세요")
                @DecimalMin(value = "0.0", inclusive = false)
                @DecimalMax(value = "1.0")
                BigDecimal maxDetourRatio) {}
