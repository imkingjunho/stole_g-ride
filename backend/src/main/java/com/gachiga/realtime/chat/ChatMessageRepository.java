package com.gachiga.realtime.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** {@link ChatMessage} 조회·저장. */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 재접속 시 지난 메시지 조회용 (FR-20). {@code before} 보다 이전 것만, 최신순으로 {@code pageable} 만큼 가져온다 — 호출하는 쪽이
     * 시간순으로 뒤집는다.
     */
    List<ChatMessage> findByGroupIdAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long groupId, LocalDateTime before, Pageable pageable);

    /** 그룹의 메시지를 통째로 지운다 (T1-13, FR-21). 삭제된 건수를 돌려준다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    long deleteByGroupId(Long groupId);

    /**
     * 방이 열린 지(=가장 오래된 메시지 시각) {@code cutoff} 보다 더 지난 그룹 id 목록 (T1-13).
     *
     * <p>탑승 완료 직후에는 삭제하지 않는다. 이 쿼리는 완료된 그룹과 오래 방치된 그룹 모두의 보관 기한을 적용한다.
     */
    @Query(
            "select m.groupId from ChatMessage m group by m.groupId having min(m.createdAt) <="
                + " :cutoff")
    List<Long> findExpiredGroupIds(@Param("cutoff") LocalDateTime cutoff);
}
