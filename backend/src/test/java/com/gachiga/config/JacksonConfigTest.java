package com.gachiga.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 날짜·시각이 프론트와 오가는 <b>정확한 형식</b>을 고정한다.
 *
 * <p>계약({@code contract/ride/WaitingRequest} 등)은 {@link LocalDateTime} 을 쓴다. 시간대가 없는
 * 타입이므로 JSON 에도 오프셋 없이 나가야 한다. {@code docs/api-spec.yaml} 의 예시 값이 이 형식과
 * 다르면 프론트가 그 예시대로 보냈을 때 400 이 난다 — 그래서 코드로 못 박아 둔다.
 */
@ActiveProfiles("test")
@JsonTest
// @JsonTest 는 @Configuration 의 커스터마이저를 가져오지 않는다. 명시하지 않으면 스프링 부트 기본값을
// 시험하게 되고, 이 파일의 규칙을 지워도 테스트가 통과한다
@Import(JacksonConfig.class)
class JacksonConfigTest {

    @Autowired private ObjectMapper objectMapper;

    private record Sample(LocalDateTime departAt) {}

    private record IntSample(Integer maxWaitMin) {}

    @Test
    @DisplayName("LocalDateTime 은 오프셋 없는 ISO 문자열로 나간다")
    void serializesAsIsoLocalDateTime() throws Exception {
        String json = objectMapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 10, 20, 8, 30)));

        assertThat(json).isEqualTo("{\"departAt\":\"2026-10-20T08:30:00\"}");
    }

    @Test
    @DisplayName("숫자(epoch) 배열이 아니라 문자열로 나간다")
    void doesNotSerializeAsTimestamp() throws Exception {
        String json = objectMapper.writeValueAsString(new Sample(LocalDateTime.of(2026, 10, 20, 8, 30)));

        assertThat(json).doesNotContain("[").doesNotContain("2026,10,20");
    }

    @Test
    @DisplayName("오프셋 없는 문자열을 받아들인다 — api-spec 예시가 이 형식이어야 한다")
    void acceptsIsoLocalDateTime() throws Exception {
        Sample sample = objectMapper.readValue("{\"departAt\":\"2026-10-20T08:30:00\"}", Sample.class);

        assertThat(sample.departAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 8, 30));
    }

    @Test
    @DisplayName("오프셋이 붙은 문자열(+09:00)은 거절한다 — 이 형식을 스펙 예시로 쓰면 안 된다")
    void rejectsOffsetDateTime() {
        assertThatThrownBy(
                        () ->
                                objectMapper.readValue(
                                        "{\"departAt\":\"2026-10-20T08:30:00+09:00\"}", Sample.class))
                .hasMessageContaining("LocalDateTime");
    }

    @Test
    @DisplayName("Z 가 붙은 문자열도 거절한다 — 받아 주면 UTC 값이 9시간 어긋난 채 저장된다")
    void rejectsZuluSuffix() {
        assertThatThrownBy(
                        () ->
                                objectMapper.readValue(
                                        "{\"departAt\":\"2026-10-20T08:30:00Z\"}", Sample.class))
                .hasMessageContaining("LocalDateTime");
    }

    @Test
    @DisplayName("소수 초는 받아들인다 — 오프셋만 막고 정상 형식은 막지 않는다")
    void acceptsFractionalSeconds() throws Exception {
        Sample sample = objectMapper.readValue("{\"departAt\":\"2026-10-20T08:30:00.500\"}", Sample.class);

        assertThat(sample.departAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 8, 30, 0, 500_000_000));
    }

    @Test
    @DisplayName("초가 없는 문자열도 받아들인다 — 브라우저 datetime-local 입력이 이 형식으로 보낸다")
    void acceptsWithoutSeconds() throws Exception {
        Sample sample = objectMapper.readValue("{\"departAt\":\"2026-10-20T08:30\"}", Sample.class);

        assertThat(sample.departAt()).isEqualTo(LocalDateTime.of(2026, 10, 20, 8, 30));
    }

    @Test
    @DisplayName("정수 필드에 소수가 오면 잘라 받지 않고 거절한다")
    void rejectsFloatForInteger() {
        assertThatThrownBy(() -> objectMapper.readValue("{\"maxWaitMin\":10.7}", IntSample.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.MismatchedInputException.class);
    }

    @Test
    @DisplayName("정수는 그대로 받는다")
    void acceptsInteger() throws Exception {
        assertThat(objectMapper.readValue("{\"maxWaitMin\":10}", IntSample.class).maxWaitMin()).isEqualTo(10);
    }
}
