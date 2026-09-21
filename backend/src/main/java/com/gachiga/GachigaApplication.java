package com.gachiga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 가치가(WE-Meet) 백엔드 진입점.
 *
 * <p>공통 설정은 {@code config} 패키지에 모여 있다. 주기 작업(매칭 tick·만료 처리·대기 상태 push)은
 * {@code config/SchedulingConfig} 가 전용 스레드 풀과 함께 활성화한다 (PRD §9.2).
 */
@SpringBootApplication
// 각 모듈의 @ConfigurationProperties(domain/{모듈}.yml 을 읽는 클래스)를 자동으로 등록한다.
// 모듈 소유자는 자기 패키지에 클래스를 만들기만 하면 된다.
@ConfigurationPropertiesScan
public class GachigaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GachigaApplication.class, args);
    }
}
