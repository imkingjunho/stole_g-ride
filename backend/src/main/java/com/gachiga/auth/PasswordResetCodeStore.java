package com.gachiga.auth;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 가입 코드와 분리. 코드 소비·실패 횟수를 한 Redis 연산으로 처리해 재사용을 막는다. */
@Component
@RequiredArgsConstructor
public class PasswordResetCodeStore {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> CONSUME =
            new DefaultRedisScript<>(
                    """
        local failures=tonumber(redis.call('GET',KEYS[2]) or '0')
        if failures>=5 then return -2 end
        local saved=redis.call('GET',KEYS[1])
        if not saved then return -1 end
        if saved~=ARGV[1] then
          local count=redis.call('INCR',KEYS[2])
          if count==1 then redis.call('EXPIRE',KEYS[2],1800) end
          if count>=5 then return -2 end
          return 0
        end
        redis.call('DEL',KEYS[1])
        redis.call('DEL',KEYS[2])
        return 1
        """,
                    Long.class);

    public boolean reserveSend(String email) {
        return Boolean.TRUE.equals(
                redis.opsForValue()
                        .setIfAbsent("password-reset:send:" + email, "1", Duration.ofSeconds(60)));
    }

    public void save(String email, String code) {
        redis.opsForValue()
                .set(
                        "password-reset:code:" + email,
                        TokenService.hash(code),
                        Duration.ofMinutes(10));
    }

    public void clearCode(String email) {
        redis.delete("password-reset:code:" + email);
    }

    public long consume(String email, String code) {
        Long result =
                redis.execute(
                        CONSUME,
                        List.of("password-reset:code:" + email, "password-reset:fail:" + email),
                        TokenService.hash(code));
        return result == null ? -1 : result;
    }
}
