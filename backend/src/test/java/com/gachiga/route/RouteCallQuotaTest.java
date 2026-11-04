package com.gachiga.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RouteCallQuotaTest {

    @Test
    void parsesAllowedDecisionAndWarnThresholds() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn("1:8:800:1:1:0")
                .when(redisTemplate)
                .execute(
                        anyRedisScript(),
                        org.mockito.ArgumentMatchers.<List<String>>any(),
                        any(Object[].class));
        RouteCallQuota quota = new RouteCallQuota(
                redisTemplate,
                10,
                30,
                1000,
                Clock.fixed(Instant.parse("2026-10-28T02:00:15Z"), ZoneOffset.UTC));

        RouteCallQuota.Decision decision = quota.acquire();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.tickCount()).isEqualTo(8);
        assertThat(decision.dailyCount()).isEqualTo(800);
        assertThat(decision.tickWarning()).isTrue();
        assertThat(decision.dailyWarning()).isTrue();
        assertThat(decision.limit()).isEqualTo(RouteCallQuota.Limit.NONE);
    }

    @Test
    void parsesDailyLimitDecision() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn("0:10:1000:0:0:2")
                .when(redisTemplate)
                .execute(
                        anyRedisScript(),
                        org.mockito.ArgumentMatchers.<List<String>>any(),
                        any(Object[].class));
        RouteCallQuota quota = new RouteCallQuota(
                redisTemplate,
                10,
                30,
                1000,
                Clock.fixed(Instant.parse("2026-10-28T02:00:15Z"), ZoneOffset.UTC));

        RouteCallQuota.Decision decision = quota.acquire();

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.tickCount()).isEqualTo(10);
        assertThat(decision.dailyCount()).isEqualTo(1000);
        assertThat(decision.limit()).isEqualTo(RouteCallQuota.Limit.DAILY);
    }

    @Test
    void configuresBothCountersToExpire() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doReturn("1:1:1:0:0:0")
                .when(redisTemplate)
                .execute(
                        anyRedisScript(),
                        org.mockito.ArgumentMatchers.<List<String>>any(),
                        any(Object[].class));
        RouteCallQuota quota = new RouteCallQuota(
                redisTemplate,
                10,
                30,
                1000,
                Clock.fixed(Instant.parse("2026-10-28T02:00:15Z"), ZoneOffset.UTC));
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.captor();
        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.captor();

        quota.acquire();

        verify(redisTemplate)
                .execute(
                        anyRedisScript(),
                        keys.capture(),
                        arguments.capture());
        assertThat(keys.getValue()).hasSize(2);
        assertThat(keys.getValue().get(0)).contains("tick:");
        assertThat(keys.getValue().get(1)).contains("daily:2026-10-28");
        assertThat(arguments.getValue()).containsExactly("10", "1000", "15", "46785", "8", "800");
    }

    @SuppressWarnings("unchecked")
    private static RedisScript<String> anyRedisScript() {
        return (RedisScript<String>) any(RedisScript.class);
    }
}
