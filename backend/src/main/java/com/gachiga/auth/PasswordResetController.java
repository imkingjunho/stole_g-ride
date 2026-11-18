package com.gachiga.auth;

import com.gachiga.auth.dto.*;
import com.gachiga.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/password-reset")
@SecurityRequirements
public class PasswordResetController {
    private final PasswordResetService service;

    @PostMapping("/request")
    public ApiResponse<Void> request(@Valid @RequestBody SignupRequest request) {
        service.request(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody PasswordResetRequest request) {
        service.reset(request);
        return ApiResponse.ok();
    }
}
