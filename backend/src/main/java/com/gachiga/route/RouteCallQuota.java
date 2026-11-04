package com.gachiga.route;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.StringTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RouteCallQuota {

    private static final Logger log = LoggerFactory.getLogger(RouteCallQuota.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String KEY_PREFIX = "route:quota:{global}:";
    private static final DefaultRedisScript<String> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            """
            local tick = tonumber(redis.call('GET', KEYS[1]) or '0')
            local daily = tonumber(redis.call('GET', KEYS[2]) or '0')
            local tick_limit = tonumber(ARGV[1])
            local daily_limit = tonumber(ARGV[2])
            if daily >= daily_limit then
              return table.concat({0, tick, daily, 0, 0, 2}, ':')
            end
            if tick >= tick_limit then
              return table.concat({0, tick, daily, 0, 0, 1}, ':')
            end
            tick = redis.call('INCR', KEYS[1])
            if tick == 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
            daily = redis.call('INCR', KEYS[2])
            if daily == 1 then redis.call('EXPIRE', KEYS[2], ARGV[4]) end
            local tick_warning = tick == tonumber(ARGV[5]) and 1 or 0
            local daily_warning = daily == tonumber(ARGV[6]) and 1 or 0
            return table.concat({1, tick, daily, tick_warning, daily_warning, 0}, ':')
            """,
            String.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxCallsPerTick;
    private final int tickWindowSeconds;
    private final int dailyCallLimit;
    private final Clock clock;

    @Autowired
    public RouteCallQuota(
            StringRedisTemplate redisTemplate,
            @Value("${gachiga.route.max-calls-per-tick:10}") int maxCallsPerTick,
            @Value("${gachiga.route.tick-window-seconds:30}") int tickWindowSeconds,
            @Value("${gachiga.route.daily-call-limit:1000}") int dailyCallLimit) {
        this(
                redisTemplate,
                maxCallsPerTick,
                tickWindowSeconds,
                dailyCallLimit,
                Clock.systemUTC());
    }

    RouteCallQuota(
            StringRedisTemplate redisTemplate,
            int maxCallsPerTick,
            int tickWindowSeconds,
            int dailyCallLimit,
            Clock clock) {
        if (maxCallsPerTick <= 0 || tickWindowSeconds <= 0 || dailyCallLimit <= 0) {
            throw new IllegalArgumentException("카카오 호출 한도와 tick 주기는 1 이상이어야 합니다.");
        }
        this.redisTemplate = redisTemplate;
        this.maxCallsPerTick = maxCallsPerTick;
        this.tickWindowSeconds = tickWindowSeconds;
        this.dailyCallLimit = dailyCallLimit;
        this.clock = clock;
    }

    public Decision acquire() {
        Instant now = clock.instant();
        long epochSecond = now.getEpochSecond();
        long tickBucket = epochSecond / tickWindowSeconds;
        int tickTtlSeconds = (int) (tickWindowSeconds - epochSecond % tickWindowSeconds);
        ZonedDateTime seoulNow = now.atZone(SEOUL);
        LocalDate today = seoulNow.toLocalDate();
        int dailyTtlSeconds = (int) Duration.between(
                        seoulNow, today.plusDays(1).atStartOfDay(SEOUL))
                .toSeconds();
        String tickKey = KEY_PREFIX + "tick:" + tickBucket;
        String dailyKey = KEY_PREFIX + "daily:" + today;
        int tickWarningThreshold = warningThreshold(maxCallsPerTick);
        int dailyWarningThreshold = warningThreshold(dailyCallLimit);

        String response = redisTemplate.execute(
                ACQUIRE_SCRIPT,
                List.of(tickKey, dailyKey),
                Integer.toString(maxCallsPerTick),
                Integer.toString(dailyCallLimit),
                Integer.toString(tickTtlSeconds),
                Integer.toString(dailyTtlSeconds),
                Integer.toString(tickWarningThreshold),
                Integer.toString(dailyWarningThreshold));
        Decision decision = parseDecision(response);
        if (decision.tickWarning()) {
            log.warn(
                    "카카오 경로 API tick 호출량이 80%에 도달했습니다: {}/{}",
                    decision.tickCount(),
                    maxCallsPerTick);
        }
        if (decision.dailyWarning()) {
            log.warn(
                    "카카오 경로 API 일일 호출량이 80%에 도달했습니다: {}/{}",
                    decision.dailyCount(),
                    dailyCallLimit);
        }
        return decision;
    }

    private int warningThreshold(int limit) {
        return Math.max(1, (int) Math.ceil(limit * 0.8));
    }

    private Decision parseDecision(String response) {
        if (response == null) {
            throw new IllegalStateException("Redis 호출량 제한 응답이 비어 있습니다.");
        }
        StringTokenizer tokens = new StringTokenizer(response, ":");
        if (tokens.countTokens() != 6) {
            throw new IllegalStateException("Redis 호출량 제한 응답 형식이 올바르지 않습니다.");
        }
        boolean allowed = parseFlag(tokens.nextToken());
        int tickCount = Integer.parseInt(tokens.nextToken());
        int dailyCount = Integer.parseInt(tokens.nextToken());
        boolean tickWarning = parseFlag(tokens.nextToken());
        boolean dailyWarning = parseFlag(tokens.nextToken());
        int limitCode = Integer.parseInt(tokens.nextToken());
        Limit limit = switch (limitCode) {
            case 0 -> Limit.NONE;
            case 1 -> Limit.TICK;
            case 2 -> Limit.DAILY;
            default -> throw new IllegalStateException("알 수 없는 Redis 호출량 제한 상태입니다.");
        };
        return new Decision(allowed, tickCount, dailyCount, tickWarning, dailyWarning, limit);
    }

    private boolean parseFlag(String value) {
        if ("1".equals(value)) {
            return true;
        }
        if ("0".equals(value)) {
            return false;
        }
        throw new IllegalStateException("Redis 호출량 제한 플래그가 올바르지 않습니다.");
    }

    public enum Limit {
        NONE,
        TICK,
        DAILY
    }

    public record Decision(
            boolean allowed,
            int tickCount,
            int dailyCount,
            boolean tickWarning,
            boolean dailyWarning,
            Limit limit) {}
}
