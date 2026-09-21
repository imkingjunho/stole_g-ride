package com.gachiga.realtime.chat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휘발성 채팅 — 개설 후 일정 시간이 지난 방의 메시지를 물리 삭제한다 (T1-13, FR-21).
 *
 * <p>탑승 완료({@code GroupCompleted})는 {@link ChatRoomEventListener} 가 즉시 지운다. 이 스케줄러는
 * 그와 별개로, 완료 이벤트를 못 받았거나 방이 오래 방치된 경우까지 잡아내는 안전망이다. 방의
 * "개설 시각"은 그 방의 가장 오래된 메시지 시각(T1-12 안내 메시지)으로 본다.
 *
 * <p>주기는 {@code gachiga.chat.cleanup-interval-seconds}(기본 60초)다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageCleanupScheduler {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatProperties chatProperties;
    private final Clock clock;

    /**
     * 삭제 한 번.
     *
     * <p>{@code fixedDelayString} 은 이전 실행이 끝난 뒤부터 센다. DB 가 느릴 때 실행이 겹쳐 같은
     * 그룹을 두 번 처리하는 일을 막는다.
     */
    @Scheduled(
            fixedDelayString = "${gachiga.chat.cleanup-interval-seconds}",
            timeUnit = TimeUnit.SECONDS)
    @Transactional
    public void deleteExpiredMessages() {
        LocalDateTime cutoff =
                LocalDateTime.now(clock).minusHours(chatProperties.retentionHours());
        List<Long> expiredGroupIds = chatMessageRepository.findExpiredGroupIds(cutoff);
        if (expiredGroupIds.isEmpty()) {
            return;
        }

        long totalDeleted = 0;
        for (Long groupId : expiredGroupIds) {
            long deleted = chatMessageRepository.deleteByGroupId(groupId);
            totalDeleted += deleted;
            log.info(
                    "채팅 메시지 물리 삭제 groupId={} 개설 {}시간 경과 {}건",
                    groupId,
                    chatProperties.retentionHours(),
                    deleted);
        }
        log.info("휘발성 채팅 정리 {}개 방 {}건", expiredGroupIds.size(), totalDeleted);
    }
}
