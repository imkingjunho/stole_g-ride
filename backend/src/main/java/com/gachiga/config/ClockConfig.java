package com.gachiga.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "지금"을 주입받을 수 있게 한다.
 *
 * <p>{@code LocalDateTime.now()} 를 코드에서 직접 부르면 두 가지가 곤란하다. 서버 시간대에
 * 따라 값이 달라지고, 테스트에서 시각을 고정할 수 없다. 만료 처리(FR-10)나 남은 시간 계산처럼
 * 시각이 곧 로직인 곳이 많으므로 빈으로 둔다.
 *
 * <p>테스트에서는 {@code Clock.fixed(...)} 를 주입해 "지금"을 원하는 값으로 못 박으면 된다.
 */
@Configuration
public class ClockConfig {

    /** 서버가 어디서 돌든 한국 시간을 쓴다 (PRD §7.1 의 시각들이 전부 KST 기준) */
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
