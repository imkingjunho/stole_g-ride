package com.gachiga.realtime.chat;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.contract.matching.MatchHistoryPort;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Optional;

class ChatAccessTest {
    final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 17, 0);
    final Clock clock =
            Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    final MatchHistoryPort groups = mock(MatchHistoryPort.class);
    final ChatAccess access = new ChatAccess(groups, new ChatProperties(3, 60), clock);

    @Test
    void completionDoesNotCloseChatWithinRetention() {
        when(groups.confirmedAt(1L)).thenReturn(Optional.of(now.minusHours(2)));
        assertThatCode(() -> access.requireOpen(1L)).doesNotThrowAnyException();
    }

    @Test
    void expiresExactlyAtThreeHoursAndCannotReopen() {
        when(groups.confirmedAt(1L)).thenReturn(Optional.of(now.minusHours(3)));
        assertThatThrownBy(() -> access.requireOpen(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void unconfirmedOrDissolvedGroupCannotChat() {
        when(groups.confirmedAt(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> access.requireOpen(1L)).isInstanceOf(BusinessException.class);
    }
}
