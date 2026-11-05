package com.gachiga.ride;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code ride} 모듈 설정 — {@code resources/domain/ride.yml} 의 {@code gachiga.ride.*} 를 읽는다.
 *
 * <p>값을 코드에 박지 않고 여기로 모으는 이유는, 시연 중에 대기 시간이나 최소 거리를 바꿔 보려면
 * 재컴파일 없이 yml 만 고칠 수 있어야 하기 때문이다 (CLAUDE.md §5).
 *
 * @param allowedMaxWaitMinutes 사용자가 고를 수 있는 최대 대기 시간(분) 목록 (FR-07)
 * @param defaultMaxWaitMinutes 화면 기본 선택값(분)
 * @param allowedDetourRatios   사용자가 고를 수 있는 최대 우회율 목록 (FR-07)
 * @param minDistanceMeters     거점~목적지 최소 거리(m). 미만이면 요청을 거절한다 (E-08)
 * @param matchCutoffSeconds    만료가 이만큼도 안 남으면 새 그룹에 넣지 않는다 (E-04)
 * @param expiryIntervalSeconds 만료 처리 스케줄러 주기(초) (FR-10)
 */
@ConfigurationProperties(prefix = "gachiga.ride")
public record RideProperties(
        List<Integer> allowedMaxWaitMinutes,
        int defaultMaxWaitMinutes,
        List<BigDecimal> allowedDetourRatios,
        int minDistanceMeters,
        int matchCutoffSeconds,
        int expiryIntervalSeconds) {}
