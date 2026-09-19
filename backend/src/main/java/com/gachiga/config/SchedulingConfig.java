package com.gachiga.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@code @Scheduled} 를 켜고, 주기 작업이 쓸 스레드 풀을 따로 만든다.
 *
 * <p>PRD §9.2 의 주기 작업은 세 가지다.
 *
 * <ul>
 *   <li>{@code matching} 30초 — 매칭 tick (서준)
 *   <li>{@code ride} 30초 — 만료 처리 (이승민, T1-7)
 *   <li>{@code realtime} 5초 — 대기 상태 push / 1분 — 채팅 삭제 (임승현)
 * </ul>
 *
 * <p><b>풀을 직접 만드는 이유</b>가 두 가지 있다. 첫째, 스프링 기본값은 스레드 <b>한 개</b>라서
 * 한 작업이 늦어지면 나머지가 줄줄이 밀린다. 둘째, {@code realtime/WebSocketConfig} 가 등록하는
 * STOMP 전용 스케줄러에 주기 작업이 얹히면 메시지 전송이 지연될 수 있다.
 *
 * <p>빈 이름을 {@code taskScheduler} 로 두는 것이 중요하다. 컨텍스트에 스케줄러가 둘 이상일 때
 * 스프링은 이 이름을 보고 {@code @Scheduled} 가 쓸 것을 고른다.
 *
 * <p><b>알아 둘 것:</b> 이 컨텍스트에는 {@code Executor} 타입 빈이 있어 스프링 부트의
 * {@code applicationTaskExecutor} 자동 구성이 물러난다. 그래서 {@code @Async} 나 MVC 비동기를
 * 쓰면 요청마다 스레드를 새로 만드는 기본 실행기로 떨어지고 {@code spring.task.execution.*} 설정도
 * 먹지 않는다. 비동기를 쓰기 시작할 때 전용 실행기 빈을 여기에 추가한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    /** 모듈 3개가 동시에 돌아도 서로 밀리지 않을 만큼 */
    private static final int POOL_SIZE = 4;

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        // 로그에서 어느 작업이 도는지 알아보기 쉽게 한다
        scheduler.setThreadNamePrefix("gachiga-sched-");
        // 종료할 때 돌고 있던 작업은 끝까지 기다린다 (만료 처리 도중 끊기면 상태가 어긋난다)
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        return scheduler;
    }
}
