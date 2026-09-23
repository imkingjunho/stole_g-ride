package com.gachiga.ride.dev;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데모 시뮬레이터 설정 — {@code resources/domain/ride.yml} 의 {@code gachiga.ride.simulator.*} 를 읽는다.
 *
 * <p>시연 중에 건수 상한이나 목적지 거리를 바꿔 보려면 재컴파일 없이 yml 만 고칠 수 있어야 한다.
 *
 * @param maxCount          한 번에 넣을 수 있는 최대 건수
 * @param userScanLimit     사용자 id 를 몇 개까지 시도해 볼지. 이만큼 시도해도 모자라면 멈춘다
 * @param minDistanceMeters 거점~가상 목적지 최소 거리(m). 요청 최소 거리(500m)보다 넉넉히 잡는다
 * @param maxDistanceMeters 거점~가상 목적지 최대 거리(m)
 * @param corridors         목적지를 몰아 뿌릴 방향 갈래 수. 적을수록 경로가 겹쳐 매칭이 잘 난다
 */
@ConfigurationProperties(prefix = "gachiga.ride.simulator")
public record SimulatorProperties(
        int maxCount,
        int userScanLimit,
        int minDistanceMeters,
        int maxDistanceMeters,
        int corridors) {}
