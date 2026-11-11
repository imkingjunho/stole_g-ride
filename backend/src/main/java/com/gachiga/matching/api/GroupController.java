package com.gachiga.matching.api;

import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;
import com.gachiga.matching.GroupService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @GetMapping("/{groupId}")
    public ApiResponse<GroupResponse> getGroup(
            @PathVariable Long groupId, @CurrentUser Long userId) {
        return ApiResponse.ok(groupService.detail(groupId, userId));
    }

    @PostMapping("/{groupId}/complete")
    public ApiResponse<Void> completeGroup(@PathVariable Long groupId, @CurrentUser Long userId) {
        groupService.complete(groupId, userId);
        return ApiResponse.ok();
    }
}
