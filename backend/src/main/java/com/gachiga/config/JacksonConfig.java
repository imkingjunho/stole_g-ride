package com.gachiga.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.LocalDateTime;
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
            builder.featuresToDisable(
                    // LocalDateTime 등을 [2026,10,20,8,31] 배열이 아니라 ISO 문자열로 쓴다
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                    // 정수 필드에 10.7 이 오면 10 으로 잘라 받지 않고 400 으로 거절한다.
                    // 명세가 integer 로 정한 값이다 (api-spec maxWaitMin·grade 등)
                    DeserializationFeature.ACCEPT_FLOAT_AS_INT);
            // "2026-10-20T08:30:00Z" 를 받으면 Z 를 떼고 08:30 으로 읽는다 — 한국 시각으로는 17:30 인데
            // 9시간 어긋난 채 저장된다. 브라우저의 toISOString() 이 바로 이 형식이다.
            // 엄격 모드로 두면 오프셋(Z·+09:00)이 붙은 값은 400 이 된다 (api-spec 머리말 "시각 형식")
            builder.postConfigurer(
                    objectMapper ->
                            objectMapper
                                    .configOverride(LocalDateTime.class)
                                    .setFormat(JsonFormat.Value.forLeniency(false)));
        };
    }
}
