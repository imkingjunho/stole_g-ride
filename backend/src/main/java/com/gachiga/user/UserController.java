package com.gachiga.user;

import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;
import com.gachiga.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 프로필 API (PRD §8). */
@Tag(name = "users", description = "회원 프로필 (임승현)")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            operationId = "getUsersMe",
            summary = "[P0] 내 프로필",
            description = "실명·전화번호는 담지 않는다 (PRD §2.2). 로그인하지 않았으면 UNAUTHORIZED.")
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@CurrentUser Long userId) {
        return ApiResponse.ok(userService.myProfile(userId));
    }
}
