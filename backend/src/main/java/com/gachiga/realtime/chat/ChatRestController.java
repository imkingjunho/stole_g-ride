package com.gachiga.realtime.chat;

import com.gachiga.common.response.ApiResponse;
import com.gachiga.contract.auth.CurrentUser;
import com.gachiga.realtime.chat.dto.ChatMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 채팅 이전 메시지 조회 API (PRD §8·§14.5). */
@Tag(name = "groups", description = "채팅 이전 메시지 조회 (임승현)")
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @Operation(
            operationId = "getGroupsByGroupIdMessages",
            summary = "[P0] 채팅 이전 메시지 조회 (재접속용)",
            description =
                    "실시간 수신은 WebSocket /topic/chat/{groupId} 가 맡는다. 방에 다시 들어왔을 때 지난"
                        + " 메시지를 채우는 용도다. 메시지는 탑승 완료 또는 3시간 경과 시 물리 삭제되므로"
                        + " (FR-21) 빈 배열이 정상일 수 있다. 그룹 구성원이 아니면 GROUP_NOT_MEMBER.")
    @GetMapping("/{groupId}/messages")
    public ApiResponse<List<ChatMessageResponse>> messages(
            @CurrentUser Long userId,
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime before,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(chatService.recentMessages(groupId, userId, before, size));
    }
}
