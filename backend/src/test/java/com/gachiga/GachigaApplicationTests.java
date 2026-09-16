package com.gachiga;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링 컨텍스트가 정상적으로 조립되는지 검증한다.
 *
 * <p>외부 인프라(MySQL·Redis) 없이 돌아가도록 test 프로파일에서 DataSource·JPA
 * 자동설정을 제외한다. 실제 DB 연동 검증은 T1-4(Docker Compose) 이후 통합 테스트로 분리한다.
 */
@ActiveProfiles("test")
@SpringBootTest
class GachigaApplicationTests {

    @Test
    void contextLoads() {
        // 컨텍스트 로딩 실패 시 테스트가 실패한다
    }
}
