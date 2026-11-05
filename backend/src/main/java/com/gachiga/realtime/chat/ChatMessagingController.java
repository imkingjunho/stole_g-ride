package com.gachiga.realtime.chat;

import com.gachiga.realtime.StompPrincipal;
import com.gachiga.realtime.chat.dto.ChatSendRequest;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * {@code /app/chat/{groupId}} 발신 처리 (T1-11, PRD §14.5).
 *
 * <p>저장·인가·broadcast 는 {@link ChatService} 가 맡는다 — 여기는 STOMP 프레임을 해석해 넘기는
 * 일만 한다.
 */
@Controller
@RequiredArgsConstructor
public class ChatMessagingController {

    private final ChatService chatService;

    @MessageMapping("/chat/{groupId}")
    public void send(
            @DestinationVariable Long groupId,
            Principal principal,
            @Payload ChatSendRequest request) {
        chatService.send(groupId, StompPrincipal.userIdOf(principal), request);
    }
}
