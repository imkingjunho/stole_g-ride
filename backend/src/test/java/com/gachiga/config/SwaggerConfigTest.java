package com.gachiga.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Swagger 산출물({@code /v3/api-docs})이 명세와 어긋나지 않게 하는 설정을 지킨다.
 *
 * <p>{@code @CurrentUser Long userId} 를 springdoc 이 필수 쿼리 파라미터로 오해하던 것을 막았다(T2-2 REQ-9).
 * 설정 한 줄이 지워져도 다른 테스트는 다 통과하므로 여기서 직접 확인한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("@CurrentUser 는 어느 연산에도 userId 쿼리 파라미터로 나오지 않는다")
    void currentUserIsNotAParameter() throws Exception {
        String body =
                mockMvc.perform(get("/v3/api-docs"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        JsonNode paths = objectMapper.readTree(body).path("paths");

        List<String> leaked = new ArrayList<>();
        paths.fields()
                .forEachRemaining(
                        path ->
                                path.getValue()
                                        .fields()
                                        .forEachRemaining(
                                                op ->
                                                        op.getValue()
                                                                .path("parameters")
                                                                .forEach(
                                                                        param -> {
                                                                            if ("userId".equals(param.path("name").asText())) {
                                                                                leaked.add(op.getKey() + " " + path.getKey());
                                                                            }
                                                                        })));

        // 연산이 하나도 없으면 검사 자체가 무의미하다
        assertThat(paths.size()).isPositive();
        assertThat(leaked).as("userId 쿼리 파라미터가 새어 나온 연산").isEmpty();
    }
}
