package com.gachiga.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/** {@link ReportRepository} 를 <b>실제 DB(임베디드 H2)</b>로 검증한다 — 엔티티·컬럼 매핑이 맞는지 본다. */
@ActiveProfiles("test")
@DataJpaTest
class ReportRepositoryTest {

    @Autowired private ReportRepository repository;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Test
    @DisplayName("저장하고 그대로 읽어 온다")
    void savesAndReadsBack() {
        Report saved =
                repository.saveAndFlush(
                        Report.create(1L, 2L, 17L, ReportReason.NO_SHOW, "안 왔어요", NOW));

        Report found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getReporterId()).isEqualTo(1L);
        assertThat(found.getReportedId()).isEqualTo(2L);
        assertThat(found.getGroupId()).isEqualTo(17L);
        assertThat(found.getReason()).isEqualTo(ReportReason.NO_SHOW);
        assertThat(found.getDetail()).isEqualTo("안 왔어요");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("detail 은 선택 입력이라 없어도 된다")
    void detailIsOptional() {
        Report saved =
                repository.saveAndFlush(Report.create(1L, 2L, 17L, ReportReason.OTHER, null, NOW));

        assertThat(repository.findById(saved.getId()).orElseThrow().getDetail()).isNull();
    }
}
