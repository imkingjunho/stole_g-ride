package com.gachiga.matching;

import static org.assertj.core.api.Assertions.*;

import com.gachiga.matching.adapter.RideHistoryAdapter;
import com.gachiga.matching.domain.*;
import com.gachiga.matching.port.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

@DataJpaTest
@ActiveProfiles("test")
class RideHistoryIntegrationTest {
    @Autowired MatchMemberRepository members;
    @Autowired MatchGroupRepository groups;
    @Autowired jakarta.persistence.EntityManager em;

    MatchGroup group(String hub) {
        var group = groups.saveAndFlush(MatchGroup.builder().hubId(1L).hubName(hub).build());
        group.confirm();
        return group;
    }

    void member(
            MatchGroup group,
            long userId,
            boolean completed,
            LocalDateTime at,
            int fare,
            int saving) {
        members.saveAndFlush(
                MatchMember.builder()
                        .groupId(group.getId())
                        .requestId(userId + group.getId() * 10)
                        .userId(userId)
                        .boardingOrder(1)
                        .dropoffOrder(1)
                        .destName("목적지")
                        .soloFare(fare)
                        .shareAmount(fare - saving)
                        .savingAmount(saving)
                        .completed(completed)
                        .completedAt(at)
                        .build());
    }

    @Test
    void ownCompletionIsVisibleBeforeEveryoneCompletesAndStatsSpanPages() {
        var nov = LocalDateTime.of(2026, 11, 1, 0, 0);
        var g = group("후문");
        member(g, 1, true, nov, 10000, 2000);
        member(g, 2, false, null, 10000, 1000);
        var october = group("정문");
        member(october, 1, true, nov.minusNanos(1000), 20000, 10000);
        em.flush();
        em.clear();
        var adapter = new RideHistoryAdapter(members);
        var page = adapter.history(1L, nov, nov.plusMonths(1), 0, 1);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().get(0).memberCount()).isEqualTo(2);
        assertThat(page.content().get(0).groupId()).isEqualTo(g.getId());
        assertThat(adapter.history(2L, null, null, 0, 20).totalElements()).isZero();
        var stats = adapter.stats(1L);
        assertThat(stats.totalRides()).isEqualTo(2);
        assertThat(stats.totalSaving()).isEqualTo(12000);
        assertThat(stats.averageSavingRate()).isCloseTo(.35, within(.00001));
        assertThat(adapter.history(1L, null, null, 0, 1).last()).isFalse();
        assertThat(adapter.history(1L, null, null, 1, 1).last()).isTrue();
        assertThat(adapter.stats(99L).totalRides()).isZero();
    }

    @Test
    void repeatedCompletionKeepsFirstTimestampAndOneHistoryEntry() {
        var g = group("후문");
        member(g, 1, false, null, 10000, 2000);
        var m = members.findByGroupIdAndUserId(g.getId(), 1L).orElseThrow();
        m.complete();
        var first = m.getCompletedAt();
        m.complete();
        assertThat(m.getCompletedAt()).isEqualTo(first);
        assertThat(new RideHistoryAdapter(members).history(1L, null, null, 0, 20).totalElements())
                .isEqualTo(1);
    }
}
