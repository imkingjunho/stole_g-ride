package com.gachiga.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachiga.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link GlobalExceptionHandler} 가 모든 예외를 {@link ApiResponse} 형태로 바꾸는지 검증한다
 * (T0-2 완료 기준 · PRD §15.2 0-2).
 *
 * <p>아래 {@link TestController} 는 <b>테스트 전용</b>이다. 실제 API 가 아니므로 {@code src/test} 에만 있고
 * 운영 서버에는 존재하지 않는다.
 *
 * <p>{@code addFilters = false} 로 시큐리티 필터를 끈다. 인증은 임승현이 Phase 1 에서 붙이며,
 * 여기서 검증하려는 것은 예외 → 응답 변환뿐이기 때문이다.
 *
 * <p>{@code @ContextConfiguration} 으로 빈 두 개만 직접 올린다. 컴포넌트 스캔에 맡기면 이 테스트 전용
 * 컨트롤러가 다른 테스트의 컨텍스트에도 끼어들기 때문이다.
 */
@ActiveProfiles("test")
@WebMvcTest
@ContextConfiguration(
        classes = {
            GlobalExceptionHandlerTest.TestController.class,
            GlobalExceptionHandlerTest.ValidatedTestController.class,
            GlobalExceptionHandler.class
        })
@AutoConfigureMockMvc(addFilters = false)
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("성공 응답은 success=true, error=null 이다")
    void successResponseShape() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.value").value("안녕"))
                // 키가 사라지면 실패하고, 값이 null 일 때만 통과한다 (PRD §8 의 "error": null 계약)
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("① 비즈니스 예외는 ErrorCode 의 상태·코드·메시지로 나간다")
    void businessExceptionIsMappedToItsErrorCode() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict()) // ALREADY_IN_QUEUE = 409
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.error.code").value("ALREADY_IN_QUEUE"))
                .andExpect(jsonPath("$.error.message")
                        .value(ErrorCode.ALREADY_IN_QUEUE.getMessage()));
    }

    @Test
    @DisplayName("① 비즈니스 예외에 직접 넣은 메시지는 그대로 사용자에게 전달된다")
    void businessExceptionKeepsCustomMessage() throws Exception {
        mockMvc.perform(get("/test/business-custom"))
                .andExpect(status().isBadRequest()) // REQUEST_TOO_SHORT = 400
                .andExpect(jsonPath("$.error.code").value("REQUEST_TOO_SHORT"))
                .andExpect(jsonPath("$.error.message").value("거점에서 320m 떨어진 목적지입니다"));
    }

    @Test
    @DisplayName("② @Valid 검증 실패는 INVALID_INPUT 이고 어느 필드가 틀렸는지 알려 준다")
    void validationFailureIsMappedToInvalidInput() throws Exception {
        String body = "{\"name\": \"\", \"count\": 0}";

        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("name")))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("count")));
    }

    @Test
    @DisplayName("③ 예상 못 한 예외는 500 INTERNAL_ERROR 이고 내부 메시지를 노출하지 않는다")
    void unexpectedExceptionIsMaskedAsInternalError() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_ERROR.getMessage()))
                // 원인 메시지(내부 구현 상세)가 밖으로 새면 안 된다
                .andExpect(jsonPath("$.error.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("데이터베이스 커넥션"))));
    }

    @Test
    @DisplayName("스프링 MVC 표준 예외(405)도 ApiResponse 껍데기로 나간다")
    void springMvcExceptionIsAlsoWrapped() throws Exception {
        mockMvc.perform(post("/test/ok")) // GET 전용 경로에 POST
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("본문이 깨진 JSON 은 500 이 아니라 400 으로 나간다")
    void malformedJsonIsClientError() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ 깨진 JSON "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("② @Validated 클래스의 쿼리 파라미터 검증 실패도 500 이 아니라 400 INVALID_INPUT 이다")
    void constraintViolationIsClientError() throws Exception {
        mockMvc.perform(get("/test/search").param("q", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("q")))
                // 핸들러 메서드명(search.)이 사용자에게 새면 안 된다
                .andExpect(jsonPath("$.error.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("search."))));
    }

    /** 예외 세 종류를 일부러 발생시키는 테스트 전용 컨트롤러 */
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/ok")
        ApiResponse<SampleResponse> ok() {
            return ApiResponse.ok(new SampleResponse("안녕"));
        }

        @GetMapping("/business")
        ApiResponse<Void> business() {
            throw new BusinessException(ErrorCode.ALREADY_IN_QUEUE);
        }

        @GetMapping("/business-custom")
        ApiResponse<Void> businessWithMessage() {
            throw new BusinessException(ErrorCode.REQUEST_TOO_SHORT, "거점에서 320m 떨어진 목적지입니다");
        }

        @PostMapping("/validate")
        ApiResponse<Void> validate(@Valid @RequestBody SampleRequest request) {
            return ApiResponse.ok();
        }

        @GetMapping("/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("데이터베이스 커넥션 풀이 고갈되었습니다");
        }
    }

    /**
     * {@code @Validated} 가 붙은 컨트롤러. 이 경우 스프링은 내장 검증 대신 AOP 프록시를 쓰고
     * {@code ConstraintViolationException} 을 던진다. 위 {@link TestController} 와 분리한 이유는
     * 거기에 {@code @Validated} 를 붙이면 다른 테스트들의 조건까지 바뀌기 때문이다.
     */
    @Validated
    @RestController
    @RequestMapping("/test")
    static class ValidatedTestController {

        @GetMapping("/search")
        ApiResponse<Void> search(
                @RequestParam @NotBlank(message = "검색어는 비어 있을 수 없습니다") String q) {
            return ApiResponse.ok();
        }
    }

    record SampleResponse(String value) {}

    record SampleRequest(
            @NotBlank(message = "이름은 비어 있을 수 없습니다") String name,
            @Min(value = 1, message = "1 이상이어야 합니다") int count) {}
}
