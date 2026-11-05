package com.gachiga.auth;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

/**
 * 웹메일 인증 코드를 Redis 에 보관한다 (PRD §7.1 Redis 키).
 *
 * <p>키는 두 개다.
 *
 * <ul>
 *   <li>{@code verify:{email}} — 코드 자체. TTL {@link AuthProperties#verifyCodeTtlMinutes()}
 *   <li>{@code verify:fail:{email}} — 실패 횟수. 첫 실패 때 TTL {@link
 *       AuthProperties#verifyLockMinutes()} 을 걸고, 그 시간이 지나면 저절로 풀린다
 * </ul>
 *
 * <p>대기열(§14.5 {@code RideQueue})과 달리 이 값은 <b>Redis 자체가 진실의 원천</b>이라 DB 대체가
 * 없다. Redis 가 죽으면 인증도 멈춰야 하므로, 예외를 삼키지 않고 그대로 던진다.
 */
@Component
@RequiredArgsConstructor
public class VerificationCodeStore {

    private static final String CODE_PREFIX = "verify:";
    private static final String FAIL_PREFIX = "verify:fail:";

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;

    /** 코드를 저장한다. 이미 있던 코드는 덮어쓰고 TTL 도 다시 시작한다 */
    public void save(String email, String code) {
        valueOps()
                .set(
                        codeKey(email),
                        code,
                        Duration.ofMinutes(authProperties.verifyCodeTtlMinutes()));
    }

    /** 저장된 코드. 없거나 만료됐으면 빈 값 */
    public Optional<String> find(String email) {
        return Optional.ofNullable(valueOps().get(codeKey(email)));
    }

    /** 실패가 쌓여 잠겼는지 (FR-01) */
    public boolean isLocked(String email) {
        return failCount(email) >= authProperties.verifyMaxFailCount();
    }

    /**
     * 인증 실패를 한 번 기록한다.
     *
     * <p>첫 실패일 때만 TTL을 건다. 이미 세고 있는 중에 매번 새로 걸면 계속 틀리는 사람의 잠금이
     * 영영 풀리지 않는다.
     */
    public void recordFailure(String email) {
        String key = failKey(email);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(authProperties.verifyLockMinutes()));
        }
    }

    /** 인증에 성공한 뒤 두 키를 모두 지운다 */
    public void clear(String email) {
        redisTemplate.delete(codeKey(email));
        redisTemplate.delete(failKey(email));
    }

    private long failCount(String email) {
        String raw = valueOps().get(failKey(email));
        return raw == null ? 0L : Long.parseLong(raw);
    }

    private ValueOperations<String, String> valueOps() {
        return redisTemplate.opsForValue();
    }

    private String codeKey(String email) {
        return CODE_PREFIX + email;
    }

    private String failKey(String email) {
        return FAIL_PREFIX + email;
    }
}
