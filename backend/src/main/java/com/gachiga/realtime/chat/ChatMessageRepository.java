package com.gachiga.realtime.chat;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@link ChatMessage} 조회·저장. */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 재접속 시 지난 메시지 조회용 (FR-20). {@code before} 보다 이전 것만, 최신순으로 {@code
     * pageable} 만큼 가져온다 — 호출하는 쪽이 시간순으로 뒤집는다.
     */
    List<ChatMessage> findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long groupId, LocalDateTime before, Pageable pageable);
}
