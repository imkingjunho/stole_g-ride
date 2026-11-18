package com.gachiga.stats;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.contract.matching.RideHistoryPort;

import org.junit.jupiter.api.Test;

class HistoryControllerTest {
    final RideHistoryPort port = mock(RideHistoryPort.class);
    final HistoryController controller = new HistoryController(port);

    @Test
    void invalidPaginationAndMonthRejectedBeforeQuery() {
        assertThatThrownBy(() -> controller.history(1L, -1, 20, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.history(1L, 0, 51, null))
                .isInstanceOf(BusinessException.class);
        for (String month : new String[] {"", "2026-13", "2026-1", "garbage"})
            assertThatThrownBy(() -> controller.history(1L, 0, 20, month))
                    .isInstanceOf(BusinessException.class);
        verifyNoInteractions(port);
    }

    @Test
    void monthUsesExclusiveNextMonthAndAuthenticatedUser() {
        controller.history(7L, 0, 20, "2026-11");
        verify(port)
                .history(
                        7L,
                        java.time.LocalDateTime.of(2026, 11, 1, 0, 0),
                        java.time.LocalDateTime.of(2026, 12, 1, 0, 0),
                        0,
                        20);
    }
}
