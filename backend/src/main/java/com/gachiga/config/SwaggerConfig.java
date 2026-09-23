package com.gachiga.config;

import com.gachiga.contract.auth.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
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
 *
 * <p><b>공개 엔드포인트 표시(임승현 Phase 1):</b> 여기서 Bearer 인증을 <i>전역</i> 요구로 걸어 두었다.
 * {@code docs/api-spec.yaml} 은 가입·인증·로그인·토큰 재발급 4개에 {@code security: []} 로 예외를
 * 두었으므로, 해당 컨트롤러 메서드에 {@code @SecurityRequirements} 를 붙여야 Swagger 와 스펙이
 * 일치한다. 붙이지 않으면 T2-2 스펙 대조에서 그 4개가 계속 불일치로 잡힌다.
 */
@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    static {
        // @CurrentUser Long userId 를 springdoc 이 필수 쿼리 파라미터로 오해해, Swagger 에 userId 입력칸이
        // 생긴다. 서버는 그 값을 쓰지 않으므로(인증 또는 X-Dev-User 로 정해진다) 다른 사용자로 시험하는
        // 줄 착각하게 된다. api-spec 에도 없는 파라미터다
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

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
