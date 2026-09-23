package com.gachiga.ride.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.gachiga.contract.route.HubPort;
import com.gachiga.ride.RideProperties;
import com.gachiga.ride.RideRequestService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * 시뮬레이터가 local 밖에서는 뜨지 않는지. 운영 서버에 가상 요청을 넣는 문이 열리면 안 된다.
 */
@ActiveProfiles("test")
@SpringBootTest
class DemoSimulatorProfileTest {

    @Autowired private ApplicationContext context;

    @Test
    @DisplayName("test 프로파일에는 시뮬레이터 빈이 없다")
    void notLoadedOutsideLocal() {
        assertThat(context.getBeanNamesForType(DemoSimulator.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DemoSimulatorController.class)).isEmpty();
    }

    /** 의존성은 모두 가짜로 채운다. 확인할 것은 프로파일 조건뿐이다 */
    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withBean(RideRequestService.class, () -> mock(RideRequestService.class))
                .withBean(HubPort.class, () -> mock(HubPort.class))
                .withBean(
                        RideProperties.class,
                        () ->
                                new RideProperties(
                                        List.of(5, 10, 15, 20),
                                        10,
                                        List.of(new BigDecimal("0.10"), new BigDecimal("0.20")),
                                        500,
                                        30,
                                        30))
                .withBean(SimulatorProperties.class, () -> new SimulatorProperties(50, 200, 1500, 7000, 3))
                .withBean(Clock.class, Clock::systemDefaultZone)
                .withUserConfiguration(DemoSimulator.class, DemoSimulatorController.class);
    }

    @Test
    @DisplayName("local 프로파일에서는 뜬다 — @Profile 이름이 틀어지면 시연 당일에야 404 로 알게 된다")
    void loadedInLocal() {
        runner().withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(ctx -> assertThat(ctx).hasSingleBean(DemoSimulator.class).hasSingleBean(DemoSimulatorController.class));
    }

    @Test
    @DisplayName("프로파일을 지정하지 않으면 뜨지 않는다 — 운영(prod)도 같다")
    void notLoadedWithoutLocal() {
        runner().withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(DemoSimulator.class).doesNotHaveBean(DemoSimulatorController.class));
    }
}
