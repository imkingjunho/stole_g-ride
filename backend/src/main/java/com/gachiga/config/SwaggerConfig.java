package com.gachiga.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 설정. {@code http://localhost:8080/swagger-ui.html}
 *
 * <p>화면 오른쪽 위 <b>Authorize</b> 버튼에 JWT 를 넣으면 이후 요청에
 * {@code Authorization: Bearer ...} 헤더가 자동으로 붙는다. Phase 1 에 임승현이 인증을 붙인 뒤부터
 * 필요하고, Phase 0 에는 인증이 꺼져 있으므로 비워 둬도 된다.
 *
 * <p>Phase 0 에는 그 대신 {@code X-Dev-User} 헤더로 사용자를 고른다. 이 헤더는 Swagger 화면에서
 * 직접 입력할 수 없으므로 {@code curl} 을 쓰거나 기본값(1번 사용자)으로 시험한다.
 *
 * <p>운영 프로파일에서는 {@code application-prod.yml} 이 Swagger 를 통째로 끈다 — API 구조를
 * 외부에 공개할 이유가 없다.
 */
@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI gachigaOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("가치가(WE-Meet) API")
                                .description(
                                        "전남대 재학생 택시 동승 매칭 서비스. "
                                                + "계약 원본은 docs/api-spec.yaml 이며, 이 화면과 다르면 구현이 틀린 것이다 (PRD §14.4).")
                                .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER_SCHEME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("로그인 응답의 accessToken 을 넣는다")));
    }
}
