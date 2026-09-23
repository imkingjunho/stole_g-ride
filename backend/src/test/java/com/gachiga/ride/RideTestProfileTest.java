package com.gachiga.ride;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * test 프로파일에서 {@code ride} 가 바깥(Redis)에 명령을 보내지 않는지.
 *
 * <p>기동 시 대기열 재구성은 모든 거점의 키를 지우고 다시 채운다. 테스트 컨텍스트에서 켜져 있으면 기본 주소인
 * 개발용 Redis(127.0.0.1:6379)의 대기열이 {@code ./gradlew build} 한 번에 지워진다 (T2-3 리뷰 F1).
 */
@ActiveProfiles("test")
@SpringBootTest
class RideTestProfileTest {

    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("test 프로파일에서는 기동 시 대기열 재구성이 꺼져 있다")
    void queueRebuildIsOffInTests() {
        assertThat(context.getBeanNamesForType(RideQueueInitializer.class)).isEmpty();
    }
}
