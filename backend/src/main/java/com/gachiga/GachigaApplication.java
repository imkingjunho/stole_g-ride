package com.gachiga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 가치가(WE-Meet) 백엔드 진입점.
 *
 * <p>매칭 tick·만료 처리는 30초 주기 스케줄러로 동작하므로(PRD §9.2)
 * 애플리케이션 단위에서 스케줄링을 활성화한다.
 */
@EnableScheduling
@SpringBootApplication
public class GachigaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GachigaApplication.class, args);
    }
}
