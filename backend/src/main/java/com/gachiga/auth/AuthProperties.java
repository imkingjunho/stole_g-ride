package com.gachiga.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code auth} 모듈 설정 — {@code resources/domain/auth.yml} 의 {@code gachiga.auth.*} 를 읽는다.
 *
 * @param allowedEmailDomain 가입을 허용하는 웹메일 도메인. {@code @} 뒤쪽과 비교한다 (FR-01)
 * @param verifyCodeTtlMinutes 인증 코드 유효 시간(분)
 * @param verifyMaxFailCount 이 횟수만큼 틀리면 잠긴다
 * @param verifyLockMinutes 잠금이 풀릴 때까지의 시간(분)
 */
@ConfigurationProperties(prefix = "gachiga.auth")
public record AuthProperties(
        String allowedEmailDomain,
        int verifyCodeTtlMinutes,
        int verifyMaxFailCount,
        int verifyLockMinutes) {}
