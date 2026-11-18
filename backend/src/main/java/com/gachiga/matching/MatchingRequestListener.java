package com.gachiga.matching;

import com.gachiga.contract.event.RideRequestCreated;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

import java.util.concurrent.ThreadPoolExecutor;

@Component
@Slf4j
public class MatchingRequestListener {
    private final MatchingEngine engine;
    private final ThreadPoolTaskExecutor executor;

    public MatchingRequestListener(
            MatchingEngine engine,
            @Qualifier("matchingRequestExecutor") ThreadPoolTaskExecutor executor) {
        this.engine = engine;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RideRequestCreated event) {
        executor.execute(
                () -> {
                    try {
                        engine.executeTick();
                    } catch (RuntimeException e) {
                        log.warn("요청 직후 매칭 실패 — 정기 tick에서 재시도", e);
                    }
                });
    }

    @Configuration
    static class ExecutorConfig {
        @Bean
        ThreadPoolTaskExecutor matchingRequestExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(1);
            executor.setThreadNamePrefix("matching-request-");
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
            return executor;
        }
    }
}
