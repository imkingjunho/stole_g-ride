package com.gachiga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link VerificationCodeStore} 검증. Redis 서버 없이 돈다 — {@link StringRedisTemplate} 을 Mock 으로
 * 둔다 (CLAUDE.md §7).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationCodeStoreTest {

    private static final String EMAIL = "gachiga@jnu.ac.kr";
    private static final AuthProperties PROPERTIES = new AuthProperties("jnu.ac.kr", 10, 5, 30);

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private VerificationCodeStore store;

    @BeforeEach
    void setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        store = new VerificationCodeStore(redisTemplate, PROPERTIES);
    }

    @Nested
    @DisplayName("코드 저장·조회")
    class Code {

        @Test
        @DisplayName("verify:{email} 키에 TTL 10분으로 저장한다")
        void savesWithTtl() {
            store.save(EMAIL, "123456");

            verify(valueOps).set("verify:" + EMAIL, "123456", Duration.ofMinutes(10));
        }

        @Test
        @DisplayName("저장된 코드를 그대로 돌려준다")
        void findsSavedCode() {
            given(valueOps.get("verify:" + EMAIL)).willReturn("123456");

            assertThat(store.find(EMAIL)).contains("123456");
        }

        @Test
        @DisplayName("없으면 빈 값")
        void emptyWhenMissing() {
            given(valueOps.get("verify:" + EMAIL)).willReturn(null);

            assertThat(store.find(EMAIL)).isEmpty();
        }
    }

    @Nested
    @DisplayName("실패·잠금")
    class Lock {

        @Test
        @DisplayName("실패 횟수가 5 미만이면 잠기지 않는다")
        void notLockedBelowThreshold() {
            given(valueOps.get("verify:fail:" + EMAIL)).willReturn("4");

            assertThat(store.isLocked(EMAIL)).isFalse();
        }

        @Test
        @DisplayName("실패 횟수가 5 이상이면 잠긴다")
        void lockedAtThreshold() {
            given(valueOps.get("verify:fail:" + EMAIL)).willReturn("5");

            assertThat(store.isLocked(EMAIL)).isTrue();
        }

        @Test
        @DisplayName("실패 기록이 없으면 잠기지 않는다")
        void notLockedWhenNoFailures() {
            given(valueOps.get("verify:fail:" + EMAIL)).willReturn(null);

            assertThat(store.isLocked(EMAIL)).isFalse();
        }

        @Test
        @DisplayName("첫 실패에만 TTL 30분을 건다")
        void setsTtlOnlyOnFirstFailure() {
            given(redisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.increment("verify:fail:" + EMAIL)).willReturn(1L);

            store.recordFailure(EMAIL);

            verify(redisTemplate).expire("verify:fail:" + EMAIL, Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("두 번째 이후 실패는 TTL을 다시 걸지 않는다")
        void doesNotResetTtlAfterFirstFailure() {
            given(valueOps.increment("verify:fail:" + EMAIL)).willReturn(2L);

            store.recordFailure(EMAIL);

            verify(redisTemplate, never()).expire(eq("verify:fail:" + EMAIL), any(Duration.class));
        }
    }

    @Test
    @DisplayName("clear는 코드·실패 카운트 키를 모두 지운다")
    void clearRemovesBothKeys() {
        store.clear(EMAIL);

        verify(redisTemplate).delete("verify:" + EMAIL);
        verify(redisTemplate).delete("verify:fail:" + EMAIL);
    }
}
