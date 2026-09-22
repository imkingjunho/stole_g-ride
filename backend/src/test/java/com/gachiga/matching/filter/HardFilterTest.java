package com.gachiga.matching.filter;

import com.gachiga.contract.ride.WaitingRequest;
import com.gachiga.contract.user.UserPort;
import com.gachiga.contract.user.UserSummary;
import com.gachiga.contract.user.Gender;
import com.gachiga.contract.route.Coordinate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("HardFilter - 매칭 엔진 Step 1")
class HardFilterTest {

    private UserPort userPort;

    @BeforeEach
    void setUp() {
        userPort = mock(UserPort.class);
        var defaultUser = new UserSummary(2L, "User2", Gender.F, null);
        when(userPort.findById(anyLong())).thenReturn(Optional.of(defaultUser));
    }

    @Test
    @DisplayName("기본: 같은 거점, 5분 이내, 성별 호환, 목적지 1km 이내 → true")
    void test_basic_compatible() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest1 = new Coordinate(37.4979, 127.0276);
        Coordinate dest2 = new Coordinate(37.4980, 127.0280);

        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, dest1, "삼성역", now, now.plusMinutes(30), now,
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 100L, dest2, "삼성역", now.plusMinutes(3), now.plusMinutes(33), now.plusMinutes(3),
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        assertTrue(HardFilter.compatible(req1, req2, userPort));
    }

    @Test
    @DisplayName("거점 다름 → false")
    void test_different_hub() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest = new Coordinate(37.4979, 127.0276);

        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, dest, "삼성역", now, now.plusMinutes(30), now,
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 200L, dest, "삼성역", now.plusMinutes(3), now.plusMinutes(33), now.plusMinutes(3),
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        assertFalse(HardFilter.compatible(req1, req2, userPort));
    }

    @Test
    @DisplayName("시간차 6분 → false")
    void test_time_window_exceeded() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest = new Coordinate(37.4979, 127.0276);

        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, dest, "삼성역", now, now.plusMinutes(30), now,
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 100L, dest, "삼성역", now.plusMinutes(6), now.plusMinutes(36), now.plusMinutes(6),
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        assertFalse(HardFilter.compatible(req1, req2, userPort));
    }

    @Test
    @DisplayName("시간차 정확히 5분 → true")
    void test_time_window_boundary() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest = new Coordinate(37.4979, 127.0276);

        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, dest, "삼성역", now, now.plusMinutes(30), now,
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 100L, dest, "삼성역", now.plusMinutes(5), now.plusMinutes(35), now.plusMinutes(5),
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        assertTrue(HardFilter.compatible(req1, req2, userPort));
    }

    @Test
    @DisplayName("성별 필터: 여성만 요청, 상대방 여성 → true")
    void test_gender_filter_compatible() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest = new Coordinate(37.4979, 127.0276);

        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, dest, "삼성역", now, now.plusMinutes(30), now,
            true, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 100L, dest, "삼성역", now.plusMinutes(3), now.plusMinutes(33), now.plusMinutes(3),
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        assertTrue(HardFilter.compatible(req1, req2, userPort));
        verify(userPort).findById(2L);
    }

    @Test
    @DisplayName("성별 필터: 여성만 요청, 상대방 남성 → false")
    void test_gender_filter_incompatible() {
        var maleUser = new UserSummary(2L, "User2", Gender.M, null);
        when(userPort.findById(2L)).thenReturn(Optional.of(maleUser));

        LocalDateTime now = LocalDateTime.now();
        Coordinate dest = new Coordinate(37.4979, 127.0276);

        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, dest, "삼성역", now, now.plusMinutes(30), now,
            true, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 100L, dest, "삼성역", now.plusMinutes(3), now.plusMinutes(33), now.plusMinutes(3),
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        assertFalse(HardFilter.compatible(req1, req2, userPort));
    }

    @Test
    @DisplayName("목적지 거리 3km 이내 → true")
    void test_destination_within_range() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest1 = new Coordinate(37.4979, 127.0276);
        Coordinate dest2 = new Coordinate(37.5179, 127.0276);

        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, dest1, "강남역", now, now.plusMinutes(30), now,
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 100L, dest2, "강남역 근처", now.plusMinutes(3), now.plusMinutes(33), now.plusMinutes(3),
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        assertTrue(HardFilter.compatible(req1, req2, userPort));
    }

    @Test
    @DisplayName("목적지 거리 3km 초과 → false")
    void test_destination_out_of_range() {
        LocalDateTime now = LocalDateTime.now();
        Coordinate dest1 = new Coordinate(37.4979, 127.0276);
        Coordinate dest2 = new Coordinate(37.5379, 127.0276);

        WaitingRequest req1 = new WaitingRequest(
            1L, 1L, 100L, dest1, "강남역", now, now.plusMinutes(30), now,
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        WaitingRequest req2 = new WaitingRequest(
            2L, 2L, 100L, dest2, "멀리 떨어진 곳", now.plusMinutes(3), now.plusMinutes(33), now.plusMinutes(3),
            false, BigDecimal.valueOf(0.30), 3000, 3000, 0
        );

        assertFalse(HardFilter.compatible(req1, req2, userPort));
    }
}