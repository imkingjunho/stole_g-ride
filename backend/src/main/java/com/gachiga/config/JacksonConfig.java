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
 * <p><b>Jackson 설정은 이 파일 하나에만 둔다.</b> {@code application.yml} 의
 * {@code spring.jackson.*} 에 같은 값을 적으면 코드 쪽이 이겨서, yml 을 고친 사람이 왜 안 먹는지
 * 찾느라 시간을 버린다. 그래서 yml 쪽은 비워 두었다.
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
