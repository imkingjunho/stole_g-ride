package com.gachiga.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 인코더. {@code user} 모듈에 하나만 둔다.
 *
 * <p>{@link com.gachiga.contract.user.UserAccountPort} 계약대로, 비밀번호를 해시하고 맞춰 보는 일은
 * {@code user} 안에서만 일어난다 — 인코더가 여러 곳에 있으면 강도·알고리즘을 바꿀 때 한쪽만 바뀐다.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
