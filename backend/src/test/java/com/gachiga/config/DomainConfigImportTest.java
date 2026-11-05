package com.gachiga.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code application.yml} 의 {@code spring.config.import} 가 {@code domain/{모듈}.yml} 을 실제로
 * 읽는지 확인한다 (T0-5 완료 기준 · PRD §9.2).
 *
 * <p>이 테스트가 깨지면 <b>모든 모듈의 설정이 조용히 무시되고 있다</b>는 뜻이다. 값이 null 이 되어
 * 기본값으로 돌아가므로 증상이 늦게, 엉뚱한 곳에서 나타난다. 그래서 따로 검사한다.
 *
 * <p>{@code ride} 로 확인하는 이유는 이승민 소유라 값이 이미 들어 있기 때문이다. 나머지 세 파일
 * ({@code matching}·{@code auth}·{@code route})은 같은 방식으로 읽히며, 아직 비어 있어도
 * {@code optional:} 이라 기동을 막지 않는다.
 */
@ActiveProfiles("test")
@SpringBootTest
class DomainConfigImportTest {

    @Autowired private Environment environment;

    @Test
    @DisplayName("domain/ride.yml 의 값이 읽힌다")
    void importsDomainRideYml() {
        assertThat(environment.getProperty("gachiga.ride.min-distance-meters", Integer.class))
                .isEqualTo(500);
        assertThat(environment.getProperty("gachiga.ride.default-max-wait-minutes", Integer.class))
                .isEqualTo(10);
        assertThat(environment.getProperty("gachiga.ride.match-cutoff-seconds", Integer.class))
                .isEqualTo(30);
    }

    @Test
    @DisplayName("목록 형태의 값도 읽힌다")
    void readsListProperty() {
        // YAML 목록은 key[0], key[1] … 로 펼쳐지므로 Environment.getProperty 로는 못 읽는다.
        // @ConfigurationProperties 가 내부적으로 쓰는 Binder 를 써야 한다.
        List<Integer> allowed =
                Binder.get(environment)
                        .bind("gachiga.ride.allowed-max-wait-minutes", Bindable.listOf(Integer.class))
                        .orElseThrow(
                                () -> new AssertionError("gachiga.ride.allowed-max-wait-minutes 를 읽지 못했다"));

        assertThat(allowed).containsExactly(5, 10, 15, 20);
    }

    @Test
    @DisplayName("아직 비어 있는 모듈 파일이 있어도 기동을 막지 않는다")
    void emptyDomainFilesDoNotBreakStartup() {
        // 이 테스트가 도는 것 자체가 컨텍스트가 떴다는 뜻이다
        assertThat(environment.getProperty("gachiga.matching.nothing-here")).isNull();
        assertThat(environment.getProperty("gachiga.auth.nothing-here")).isNull();
        assertThat(environment.getProperty("gachiga.route.nothing-here")).isNull();
    }
}
