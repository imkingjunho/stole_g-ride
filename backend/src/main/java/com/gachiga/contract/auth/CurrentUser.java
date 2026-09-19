package com.gachiga.contract.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 붙이면 <b>현재 로그인한 사용자 id</b>가 주입된다.
 *
 * <pre>{@code
 * @PostMapping("/api/requests")
 * public ApiResponse<CreateResponse> create(@CurrentUser Long userId, @Valid @RequestBody CreateRequest request) {
 *     ...
 * }
 * }</pre>
 *
 * <p>파라미터 타입은 반드시 {@code Long} 이다. 리졸버 구현은 {@code auth/} (임승현) 소유다.
 * Phase 0 에는 {@code auth/DevCurrentUserResolver} 가 요청 헤더 {@code X-Dev-User} 값을 쓰고,
 * 헤더가 없으면 1 을 준다. Phase 1 에 JWT 에서 꺼내는 실제 리졸버로 바뀐다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
