package com.gachiga.ride;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.common.exception.GlobalExceptionHandler;
import com.gachiga.ride.dto.HubResponse;
import com.gachiga.ride.dto.RideRequestResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link RideRequestController} 의 HTTP 계약 검증.
 *
 * <p>{@code docs/api-spec.yaml} 이 약속한 상태 코드와 응답 모양을 지키는지 본다. 서비스는
 * Mock 이므로 로직이 아니라 <b>껍데기</b>만 검사한다 — 상태 코드, ApiResponse 래핑,
 * {@code @CurrentUser} 로 사용자가 넘어가는지.
 */
@ActiveProfiles("test")
@WebMvcTest(RideRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class RideRequestControllerTest {

    private static final String BODY =
            """
            {"hubId":2,"destName":"광주송정역","destLat":35.1378,"destLng":126.7902,
             "departAt":"2026-10-20T08:30:00","maxWaitMin":10,
             "sameGenderOnly":true,"maxDetourRatio":0.2}
            """;

    @Autowired private MockMvc mockMvc;

    @MockBean private RideRequestService rideRequestService;

    private RideRequestResponse sampleResponse() {
        return new RideRequestResponse(
                1L,
                1L,
                new HubResponse(2L, "전남대 후문", 35.1763d, 126.9123d, "CAMPUS"),
                "광주송정역",
                35.1378d,
                126.7902d,
                LocalDateTime.of(2026, 10, 20, 8, 30),
                LocalDateTime.of(2026, 10, 20, 8, 40),
                10,
                true,
                new BigDecimal("0.20"),
                15_466,
                15_300,
                "WAITING",
                600,
                2,
                true,
                null,
                LocalDateTime.of(2026, 10, 20, 8, 30));
    }

    @Test
    @DisplayName("생성은 201 로 응답하고 본문을 ApiResponse 로 감싼다")
    void createReturns201() throws Exception {
        given(rideRequestService.create(eq(1L), any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value(1))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("X-Dev-User 헤더의 사용자가 서비스로 넘어간다")
    void passesCurrentUser() throws Exception {
        given(rideRequestService.create(eq(3L), any())).willReturn(sampleResponse());

        mockMvc.perform(
                        post("/api/requests")
                                .header("X-Dev-User", "3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isCreated());

        verify(rideRequestService).create(eq(3L), any());
    }

    @Test
    @DisplayName("진행 중 요청이 있으면 409 ALREADY_IN_QUEUE")
    void duplicateReturns409() throws Exception {
        willThrow(new BusinessException(ErrorCode.ALREADY_IN_QUEUE))
                .given(rideRequestService)
                .create(eq(1L), any());

        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_IN_QUEUE"));
    }

    @Test
    @DisplayName("거리 미달이면 400 REQUEST_TOO_SHORT (E-08)")
    void tooShortReturns400() throws Exception {
        willThrow(new BusinessException(ErrorCode.REQUEST_TOO_SHORT))
                .given(rideRequestService)
                .create(eq(1L), any());

        mockMvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_TOO_SHORT"));
    }

    @Test
    @DisplayName("필수 필드가 빠지면 400 INVALID_INPUT")
    void missingFieldReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"hubId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("진행 중 요청이 없으면 200 이고 data 가 null 이다 — 404 가 아니다")
    void noRequestReturnsNullData() throws Exception {
        given(rideRequestService.findMyRequest(1L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/requests/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("진행 중 요청이 있으면 남은 시간·후보 수와 함께 준다")
    void returnsMyRequest() throws Exception {
        given(rideRequestService.findMyRequest(1L)).willReturn(Optional.of(sampleResponse()));

        mockMvc.perform(get("/api/requests/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingSeconds").value(600))
                .andExpect(jsonPath("$.data.candidateCount").value(2))
                .andExpect(jsonPath("$.data.estimated").value(true));
    }

    @Test
    @DisplayName("취소는 200 이고 본문이 없다")
    void cancelReturns200() throws Exception {
        mockMvc.perform(delete("/api/requests/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        verify(rideRequestService).cancel(1L, 1L);
    }

    @Test
    @DisplayName("남의 요청 취소는 403")
    void cancelOthersReturns403() throws Exception {
        willThrow(new BusinessException(ErrorCode.FORBIDDEN, "본인 요청만 취소할 수 있습니다."))
                .given(rideRequestService)
                .cancel(1L, 9L);

        mockMvc.perform(delete("/api/requests/9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
