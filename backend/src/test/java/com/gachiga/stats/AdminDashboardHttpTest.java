package com.gachiga.stats;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gachiga.auth.JwtTokenProvider;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

@SpringBootTest(properties = "gachiga.admin.user-ids=71,73")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardHttpTest {
    @Autowired MockMvc mvc;
    @MockBean JwtTokenProvider jwt;

    @Test
    void onlyConfiguredVerifiedPrincipalCanReadRoster() throws Exception {
        when(jwt.verify("operator")).thenReturn(Optional.of(73L));
        when(jwt.verify("member")).thenReturn(Optional.of(72L));
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer member"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/admin/access").header("Authorization", "Bearer member"))
                .andExpect(jsonPath("$.data").value(false));
        mvc.perform(get("/api/admin/access").header("Authorization", "Bearer operator"))
                .andExpect(jsonPath("$.data").value(true));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.daily", hasSize(7)))
                .andExpect(jsonPath("$.data.daily[0].groups").value(0))
                .andExpect(jsonPath("$.data.waiting").isArray())
                .andExpect(jsonPath("$.data.matched").isArray());
    }
}
