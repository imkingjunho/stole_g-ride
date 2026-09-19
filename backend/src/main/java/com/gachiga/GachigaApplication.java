package com.gachiga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 가치가(WE-Meet) 백엔드 진입점.
 *
 * <p>공통 설정은 {@code config} 패키지에 모여 있다. 주기 작업(매칭 tick·만료 처리·대기 상태 push)은
 * {@code config/SchedulingConfig} 가 전용 스레드 풀과 함께 활성화한다 (PRD §9.2).
 */
@SpringBootApplication
public class GachigaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GachigaApplication.class, args);
    }
}
