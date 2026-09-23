package com.gachiga.ride.dev;

import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 시뮬레이터 API (T2-1). <b>local 프로파일 전용</b> — {@code docs/api-spec.yaml} 에 없는 개발 도구다.
 *
 * <p>Swagger({@code /swagger-ui.html}) 의 {@code dev} 태그에서 바로 눌러 쓸 수 있다.
 */
@Tag(name = "dev", description = "local 전용 개발 도구 (이승민)")
@Profile("local")
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
public class DemoSimulatorController {

    private final DemoSimulator demoSimulator;

    @Operation(
            summary = "[local] 가상 매칭 요청 N건 투입",
            description =
                    "실제 요청과 같은 검증을 거친다. 사용자 id 를 fromUserId 부터 차례로 시도하며, 없는 사용자·"
                            + "이용 제한·이미 대기 중인 사용자는 건너뛴다. 호출한 사람(발표자)도 건너뛴다(skipped.CALLER). "
                            + "seed 를 주면 n번째로 들어간 요청의 목적지·옵션이 같게 나온다.")
    @PostMapping("/simulate")
    public ApiResponse<SimulationResult> simulate(
            @CurrentUser Long callerId,
            @RequestParam Long hubId,
            @RequestParam int count,
            @RequestParam(defaultValue = "1") long fromUserId,
            @RequestParam(required = false) Long seed) {
        return ApiResponse.ok(demoSimulator.simulate(hubId, count, fromUserId, seed, callerId));
    }
}
