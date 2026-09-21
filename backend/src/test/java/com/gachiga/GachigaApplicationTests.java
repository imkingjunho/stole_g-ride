package com.gachiga;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링 컨텍스트가 정상적으로 조립되는지 검증한다.
 *
 * <p>외부 인프라 없이 돌아가도록 test 프로파일이 임베디드 H2 를 쓴다. 엔티티·리포지토리·서비스
 * 배선까지 실제로 조립되므로, 빈이 빠졌거나 매핑이 틀리면 여기서 걸린다.
 *
 * <p>다만 H2 와 MySQL 은 타입·예약어가 달라 여기 통과가 MySQL 통과를 뜻하지는 않는다.
 * 스키마가 실제로 맞는지는 local 프로파일로 bootRun 해서 확인한다 (PRD §7.3).
 */
@ActiveProfiles("test")
@SpringBootTest
class GachigaApplicationTests {

    @Test
    void contextLoads() {
        // 컨텍스트 로딩 실패 시 테스트가 실패한다
    }
}
