package com.gachiga.matching;

import static org.mockito.Mockito.*;

import com.gachiga.contract.event.RideRequestCreated;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class MatchingRequestListenerTest {
    @Configuration
    static class Config {
        @Bean
        MatchingEngine engine() {
            return mock(MatchingEngine.class);
        }

        @Bean
        ThreadPoolTaskExecutor matchingRequestExecutor() {
            return mock(ThreadPoolTaskExecutor.class);
        }

        @Bean
        MatchingRequestListener listener(MatchingEngine engine, ThreadPoolTaskExecutor executor) {
            return new MatchingRequestListener(engine, executor);
        }

        @Bean
        org.springframework.transaction.event.TransactionalEventListenerFactory eventFactory() {
            return new org.springframework.transaction.event.TransactionalEventListenerFactory();
        }
    }

    @Test
    void onlyCommittedRequestsScheduleEvaluationAndRequestThreadDoesNotEvaluate() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var executor = context.getBean(ThreadPoolTaskExecutor.class);
            var engine = context.getBean(MatchingEngine.class);
            clearInvocations(executor, engine);
            var tx =
                    new TransactionTemplate(
                            new AbstractPlatformTransactionManager() {
                                protected Object doGetTransaction() {
                                    return new Object();
                                }

                                protected void doBegin(
                                        Object transaction,
                                        org.springframework.transaction.TransactionDefinition
                                                definition) {}

                                protected void doCommit(DefaultTransactionStatus status) {}

                                protected void doRollback(DefaultTransactionStatus status) {}
                            });
            tx.executeWithoutResult(
                    status -> {
                        context.publishEvent(new RideRequestCreated(1L, 1L, 1L));
                        verifyNoInteractions(executor, engine);
                        status.setRollbackOnly();
                    });
            verifyNoInteractions(executor, engine);
            tx.executeWithoutResult(
                    status -> {
                        context.publishEvent(new RideRequestCreated(2L, 1L, 1L));
                        verifyNoInteractions(executor, engine);
                    });
            var work = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(executor).execute(work.capture());
            verifyNoInteractions(engine);
            work.getValue().run();
            verify(engine).executeTick();
        }
    }
}
