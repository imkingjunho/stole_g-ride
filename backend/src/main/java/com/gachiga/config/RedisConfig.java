package com.gachiga.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 접근 설정.
 *
 * <p>쓰임새는 두 가지다.
 *
 * <ul>
 *   <li><b>대기열</b> — {@code queue:{hubId}} Sorted Set. 점수는 희망 출발 시각 (이승민, T1-4)
 *   <li><b>경로 캐시</b> — 같은 출발·도착 조합의 카카오 응답을 10분 보관 (송준호, PRD §6.2)
 * </ul>
 *
 * <p>값을 모두 <b>문자열</b>로 다루는 {@link StringRedisTemplate} 을 쓴다. 자바 직렬화로 저장하면
 * {@code redis-cli} 로 들여다볼 수 없어 디버깅이 어렵고, 클래스가 바뀌면 옛 값을 읽지 못한다.
 * 객체를 넣어야 하면 각 모듈에서 JSON 문자열로 바꿔 저장한다.
 *
 * <p>사실 스프링 부트가 같은 빈을 자동으로 만들어 준다. 그래도 여기 적어 두는 이유는
 * <b>"우리는 문자열만 쓴다"</b>는 결정을 코드로 남기기 위해서다. 나중에 누가 JSON 직렬화
 * {@code RedisTemplate} 을 넣으려 할 때 이 주석이 근거가 된다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
