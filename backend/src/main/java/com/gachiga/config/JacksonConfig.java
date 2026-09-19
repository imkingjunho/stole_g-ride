package com.gachiga.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.TimeZone;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 직렬화 규칙. 프론트와 주고받는 모든 날짜·시각의 형식을 여기서 한 번에 정한다.
 *
 * <p>날짜는 <b>ISO-8601 문자열</b>로 나간다. 예: {@code "2026-10-20T08:31:00"}.
 * 숫자(epoch) 로 나가면 프론트가 매번 변환해야 하고 사람이 눈으로 읽을 수도 없다.
 *
 * <p>시간대는 {@code Asia/Seoul} 로 고정한다. 서버가 어디서 돌든 같은 시각이 나와야 한다.
 *
 * <p>{@code application.yml} 에도 같은 설정이 있지만 여기에 코드로 두는 이유는, 설정 파일을
 * 누가 건드려도 규칙이 유지되게 하고 의도를 주석으로 남기기 위해서다.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone("Asia/Seoul"));
            // LocalDateTime 등을 [2026,10,20,8,31] 배열이 아니라 ISO 문자열로 쓴다
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
