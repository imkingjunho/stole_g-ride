package com.gachiga.auth.dto;

/**
 * 가입(T1-3)·로그인(T1-4) 성공 응답. {@code docs/api-spec.yaml} 의 {@code TokenResponse} 와 1:1이다.
 *
 * @param accessToken 유효 30분
 * @param refreshToken 유효 14일. 재발급 때마다 새것으로 바뀐다(회전)
 * @param accessTokenExpiresIn Access 토큰 남은 시간(초)
 */
public record TokenResponse(
        String accessToken, String refreshToken, int accessTokenExpiresIn, AuthUserProfile user) {}
