package com.gachiga.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gachiga.common.exception.BusinessException;
import com.gachiga.common.exception.ErrorCode;
import com.gachiga.contract.matching.MatchHistoryPort;
import com.gachiga.contract.user.Gender;
import com.gachiga.user.dto.ReportRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** {@link ReportService} 검증. DB 없이 도는 순수 로직 테스트다 (CLAUDE.md §7). */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 0);
    private static final Clock CLOCK =
            Clock.fixed(
                    NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private MatchHistoryPort matchHistoryPort;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, userRepository, matchHistoryPort, CLOCK);
    }

    private User reportedUser() {
        return User.create(
                "reported@jnu.ac.kr", "{bcrypt}hash", "정문부엉이", Gender.M, null, null, NOW);
    }

    @Nested
    @DisplayName("신고 접수")
    class Submit {

        @Test
        @DisplayName("같은 그룹 구성원이면 접수하고 대상의 신고 횟수를 올린다")
        void acceptsReportBetweenGroupMembers() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            given(matchHistoryPort.isMember(17L, 2L)).willReturn(true);
            User reported = reportedUser();
            given(userRepository.findById(2L)).willReturn(Optional.of(reported));

            reportService.report(1L, new ReportRequest(17L, 2L, ReportReason.NO_SHOW, "안 왔어요"));

            verify(reportRepository).save(any(Report.class));
            assertThat(reported.getReportCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("본인을 신고하면 INVALID_INPUT")
        void rejectsSelfReport() {
            assertThatThrownBy(
                            () ->
                                    reportService.report(
                                            1L,
                                            new ReportRequest(
                                                    17L, 1L, ReportReason.RUDE, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT);

            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("신고자가 그 그룹 구성원이 아니면 GROUP_NOT_MEMBER")
        void rejectsWhenReporterNotMember() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(false);

            assertThatThrownBy(
                            () ->
                                    reportService.report(
                                            1L,
                                            new ReportRequest(
                                                    17L, 2L, ReportReason.RUDE, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_NOT_MEMBER);

            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("신고 대상이 그 그룹 구성원이 아니면 GROUP_NOT_MEMBER")
        void rejectsWhenTargetNotMember() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            given(matchHistoryPort.isMember(17L, 2L)).willReturn(false);

            assertThatThrownBy(
                            () ->
                                    reportService.report(
                                            1L,
                                            new ReportRequest(
                                                    17L, 2L, ReportReason.RUDE, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.GROUP_NOT_MEMBER);
        }

        @Test
        @DisplayName("신고 대상 사용자가 없으면 NOT_FOUND")
        void rejectsMissingTargetUser() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            given(matchHistoryPort.isMember(17L, 2L)).willReturn(true);
            given(userRepository.findById(2L)).willReturn(Optional.empty());

            assertThatThrownBy(
                            () ->
                                    reportService.report(
                                            1L,
                                            new ReportRequest(
                                                    17L, 2L, ReportReason.RUDE, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("누적 3회가 되면 대상이 정지된다 (E-05)")
        void suspendsOnThirdAccumulatedReport() {
            given(matchHistoryPort.isMember(17L, 1L)).willReturn(true);
            given(matchHistoryPort.isMember(17L, 2L)).willReturn(true);
            User reported = reportedUser();
            reported.applyReport(NOW);
            reported.applyReport(NOW);
            given(userRepository.findById(2L)).willReturn(Optional.of(reported));

            reportService.report(1L, new ReportRequest(17L, 2L, ReportReason.UNSAFE, null));

            assertThat(reported.getReportCount()).isEqualTo(3);
            assertThat(reported.getStatus())
                    .isEqualTo(com.gachiga.contract.user.UserStatus.SUSPENDED);
        }
    }
}
